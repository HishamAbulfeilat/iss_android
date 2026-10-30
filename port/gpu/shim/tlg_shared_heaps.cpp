// SPDX-License-Identifier: GPL-2.0-or-later
// tlg: shared heaps (tlg_shared_heaps.h).
#include "tlg_shared_heaps.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#ifndef _WIN32
#include <sys/mman.h>
#include <unistd.h>
#endif

#include "video_core/renderer_vulkan/vk_instance.h"
#include "video_core/renderer_vulkan/vk_scheduler.h"

extern "C" {
typedef int (*RuntimeShareProvider)(uintptr_t address, uint64_t phys, uint64_t size);
void runtime_memory_set_share_provider(RuntimeShareProvider provider, const uint64_t* ranges,
                                       size_t count);
}

namespace TlgSharedHeaps {
namespace {

constexpr std::uint64_t MinHeapBytes = 256ull << 20;
constexpr std::size_t MaxPieces = 64;

const Vulkan::Instance* instance_ptr = nullptr;
std::uint64_t arena_block = 0;
std::uint32_t memory_type = ~0u;
bool active = false;
bool gpu_writes = false;

const vk::ExternalMemoryBufferCreateInfo arena_external{
    .handleTypes = vk::ExternalMemoryHandleTypeFlagBits::eDmaBufEXT,
};

// Written by the game thread mapping memory (runtime table lock held), read by any thread: an
// entry is complete before the count that publishes it.
std::array<Piece, MaxPieces> pieces{};
std::atomic<std::size_t> piece_count{0};
std::atomic<std::uint64_t> lowest{~0ull}, highest{0};
std::mutex add_mutex;

std::vector<std::pair<std::uint64_t, std::uint64_t>> ReadHeapFile(const std::filesystem::path& file) {
    std::vector<std::pair<std::uint64_t, std::uint64_t>> ranges;
    if (FILE* f = std::fopen(file.string().c_str(), "r")) {
        unsigned long long base, size;
        while (ranges.size() < 16 && std::fscanf(f, "%llx %llx", &base, &size) == 2) {
            if (size >= MinHeapBytes && size <= (4ull << 30)) {
                ranges.emplace_back(base, size);
            }
        }
        std::fclose(f);
    }
    return ranges;
}

/// Host-visible, CPU-cached and coherent: the game's code runs on this memory (an uncached mapping
/// would crawl) and nothing flushes caches at CPU/GPU handoffs.
std::uint32_t PickType(const vk::PhysicalDeviceMemoryProperties& props, std::uint32_t bits) {
    const auto want = vk::MemoryPropertyFlagBits::eHostVisible | vk::MemoryPropertyFlagBits::eHostCached |
                      vk::MemoryPropertyFlagBits::eHostCoherent;
    for (std::uint32_t i = 0; i < props.memoryTypeCount; ++i) {
        if ((bits & (1u << i)) && (props.memoryTypes[i].propertyFlags & want) == want) {
            return i;
        }
    }
    return ~0u;
}

vk::BufferCreateInfo SparseInfo(std::uint64_t size, vk::BufferUsageFlags usage) {
    return vk::BufferCreateInfo{
        .pNext = &arena_external,
        .flags = vk::BufferCreateFlagBits::eSparseBinding | vk::BufferCreateFlagBits::eSparseResidency,
        .size = size,
        .usage = usage,
        .sharingMode = vk::SharingMode::eExclusive,
    };
}

/// Exportable memory of `size` bytes and a dma-buf of it; false (and nothing kept) on failure.
bool AllocateExported(std::uint64_t size, vk::DeviceMemory& memory, int& fd, std::string& why) {
    const vk::Device device = instance_ptr->GetDevice();
    const vk::ExportMemoryAllocateInfo export_info{
        .handleTypes = vk::ExternalMemoryHandleTypeFlagBits::eDmaBufEXT,
    };
    const auto [memory_result, allocated] = device.allocateMemory({
        .pNext = &export_info,
        .allocationSize = size,
        .memoryTypeIndex = memory_type,
    });
    if (memory_result != vk::Result::eSuccess) {
        why = "allocation failed (" + vk::to_string(memory_result) + ")";
        return false;
    }
    const auto [fd_result, exported] = device.getMemoryFdKHR({
        .memory = allocated,
        .handleType = vk::ExternalMemoryHandleTypeFlagBits::eDmaBufEXT,
    });
    if (fd_result != vk::Result::eSuccess || exported < 0) {
        device.freeMemory(allocated);
        why = "dma-buf export failed (" + vk::to_string(fd_result) + ")";
        return false;
    }
    memory = allocated;
    fd = exported;
    return true;
}

/// Runs `record` on the graphics queue and waits; false on any failure.
template <typename Record>
bool RunOnce(Record&& record) {
    const vk::Device device = instance_ptr->GetDevice();
    const auto [pool_result, pool] =
        device.createCommandPool({.queueFamilyIndex = instance_ptr->GetGraphicsQueueFamilyIndex()});
    if (pool_result != vk::Result::eSuccess) {
        return false;
    }
    bool ok = false;
    const auto [alloc_result, cmdbufs] = device.allocateCommandBuffers(
        {.commandPool = pool, .level = vk::CommandBufferLevel::ePrimary, .commandBufferCount = 1});
    const auto [fence_result, fence] = device.createFence({});
    if (alloc_result == vk::Result::eSuccess && fence_result == vk::Result::eSuccess) {
        const vk::CommandBuffer cmd = cmdbufs[0];
        if (cmd.begin({.flags = vk::CommandBufferUsageFlagBits::eOneTimeSubmit}) ==
            vk::Result::eSuccess) {
            record(cmd);
            const vk::MemoryBarrier to_host{.srcAccessMask = vk::AccessFlagBits::eTransferWrite,
                                            .dstAccessMask = vk::AccessFlagBits::eHostRead};
            cmd.pipelineBarrier(vk::PipelineStageFlagBits::eTransfer, vk::PipelineStageFlagBits::eHost,
                                {}, to_host, {}, {});
            if (cmd.end() == vk::Result::eSuccess) {
                const vk::SubmitInfo submit{.commandBufferCount = 1, .pCommandBuffers = &cmd};
                std::scoped_lock lock{Vulkan::Scheduler::submit_mutex};
                ok = instance_ptr->GetGraphicsQueue().submit(submit, fence) == vk::Result::eSuccess &&
                     device.waitForFences(fence, true, 5'000'000'000ull) == vk::Result::eSuccess;
            }
        }
    }
    if (fence_result == vk::Result::eSuccess) {
        device.destroyFence(fence);
    }
    device.destroyCommandPool(pool);
    return ok;
}

#ifndef _WIN32
/// The whole path the game will use, on 2 MiB: an exported buffer sparse-bound into an arena-like
/// buffer, mapped by the CPU through its dma-buf. GPU write -> CPU read, then CPU write -> GPU read.
bool Probe(vk::BufferUsageFlags usage, std::string& why) {
    const vk::Device device = instance_ptr->GetDevice();
    const std::uint64_t size = std::max<std::uint64_t>(2ull << 20, arena_block);
    const std::uint64_t half = size / 2;
    const auto [buffer_result, buffer] = device.createBuffer(SparseInfo(size, usage));
    if (buffer_result != vk::Result::eSuccess) {
        why = "sparse dma-buf buffer creation failed (" + vk::to_string(buffer_result) + ")";
        return false;
    }
    vk::DeviceMemory memory{};
    int fd = -1;
    void* view = MAP_FAILED;
    bool ok = AllocateExported(size, memory, fd, why);
    if (ok) {
        view = mmap(nullptr, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
        ok = view != MAP_FAILED;
        if (!ok) {
            why = "the dma-buf cannot be mapped by the CPU";
        }
    }
    if (ok) {
        // The game's code runs on this memory: an uncached CPU mapping would crawl.
        const auto* words = static_cast<const volatile std::uint64_t*>(view);
        std::uint64_t sum = 0;
        const auto start = std::chrono::steady_clock::now();
        for (int pass = 0; pass < 4; ++pass) {
            for (std::uint64_t i = 0; i < size / 8; i += 8) {
                sum += words[i];
            }
        }
        const double seconds =
            std::chrono::duration<double>(std::chrono::steady_clock::now() - start).count();
        static volatile std::uint64_t sink;
        sink = sum;
        const double speed = seconds > 0 ? 4.0 * size / seconds / 1e6 : 1e9;
        std::printf("GPU: shared heaps probe: CPU reads %.0f MB/s\n", speed);
        if (speed < 1000.0) {
            ok = false;
            why = "CPU reads of the dma-buf are too slow (uncached mapping)";
        }
    }
    if (ok) {
        const vk::SparseMemoryBind bind{.resourceOffset = 0, .size = size, .memory = memory};
        const vk::SparseBufferMemoryBindInfo buffer_bind{.buffer = buffer, .bindCount = 1, .pBinds = &bind};
        const auto [fence_result, fence] = device.createFence({});
        ok = fence_result == vk::Result::eSuccess;
        if (ok) {
            {
                std::scoped_lock lock{Vulkan::Scheduler::submit_mutex};
                ok = instance_ptr->GetGraphicsQueue().bindSparse(
                         vk::BindSparseInfo{.bufferBindCount = 1, .pBufferBinds = &buffer_bind},
                         fence) == vk::Result::eSuccess;
            }
            ok = ok && device.waitForFences(fence, true, 5'000'000'000ull) == vk::Result::eSuccess;
            device.destroyFence(fence);
        }
        if (!ok) {
            why = "binding the exported memory into a sparse buffer failed";
        }
    }
    if (ok) {
        auto* words = static_cast<volatile std::uint32_t*>(view);
        const std::uint64_t count = half / 4;
        for (std::uint64_t i = 0; i < count; ++i) {
            words[count + i] = 0xdeadbeefu;
        }
        const bool ran = RunOnce([&](vk::CommandBuffer cmd) { cmd.fillBuffer(buffer, half, half, 0x600dcafeu); });
        std::uint64_t bad = 0;
        for (std::uint64_t i = 0; ran && i < count; ++i) {
            bad += words[count + i] != 0x600dcafeu;
        }
        ok = ran && bad == 0;
        std::printf("GPU: shared heaps probe: GPU write -> CPU read %s\n", ok ? "pass" : "FAIL");
        if (!ok) {
            why = ran ? "the CPU saw stale GPU writes" : "the GPU work failed";
        }
    }
    if (ok) {
        auto* words = static_cast<volatile std::uint32_t*>(view);
        const std::uint64_t count = half / 4;
        for (std::uint64_t i = 0; i < count; ++i) {
            words[i] = 0xa5000000u + std::uint32_t(i);
            words[count + i] = 0;
        }
        const bool ran = RunOnce([&](vk::CommandBuffer cmd) {
            cmd.copyBuffer(buffer, buffer, vk::BufferCopy{0, half, half});
        });
        std::uint64_t bad = 0;
        for (std::uint64_t i = 0; ran && i < count; ++i) {
            bad += words[count + i] != 0xa5000000u + std::uint32_t(i);
        }
        ok = ran && bad == 0;
        std::printf("GPU: shared heaps probe: CPU write -> GPU read %s\n", ok ? "pass" : "FAIL");
        if (!ok) {
            why = ran ? "the GPU read stale CPU writes" : "the GPU work failed";
        }
    }
    if (view != MAP_FAILED) {
        munmap(view, size);
    }
    if (fd >= 0) {
        close(fd);
    }
    device.destroyBuffer(buffer);
    if (memory) {
        device.freeMemory(memory);
    }
    return ok;
}

/// The runtime asks for the memory under guest [address, address + size) (table lock held).
int Provide(uintptr_t address, uint64_t /*phys*/, uint64_t size) {
    std::scoped_lock lock{add_mutex};
    const std::size_t index = piece_count.load(std::memory_order_relaxed);
    if (index == MaxPieces || ((address | size) & (arena_block - 1)) != 0) {
        return -1;
    }
    vk::DeviceMemory memory{};
    int fd = -1;
    std::string why;
    if (!AllocateExported(size, memory, fd, why)) {
        static int warned = 0;
        if (warned++ < 4) {
            std::printf("GPU: shared heap %#llx+%llu MiB: %s; it stays a copy\n",
                        static_cast<unsigned long long>(address),
                        static_cast<unsigned long long>(size >> 20), why.c_str());
        }
        return -1;
    }
    pieces[index] = Piece{address, address + size, memory};
    std::uint64_t low = lowest.load(std::memory_order_relaxed);
    while (address < low && !lowest.compare_exchange_weak(low, address)) {
    }
    std::uint64_t high = highest.load(std::memory_order_relaxed);
    while (address + size > high && !highest.compare_exchange_weak(high, address + size)) {
    }
    piece_count.store(index + 1, std::memory_order_release);
    return fd;
}
#endif

} // namespace

void Init(const Vulkan::Instance& instance, std::uint64_t block_size, vk::BufferUsageFlags arena_usage,
          const std::filesystem::path& heap_file) {
    const char* env = std::getenv("TLG_SHARED_HEAPS");
    if (!env || env[0] != '1') {
        return;
    }
#ifdef _WIN32
    std::printf("GPU: shared heaps: off (Android and Linux only)\n");
#else
    instance_ptr = &instance;
    arena_block = block_size;
    const auto off = [](const std::string& why) {
        std::printf("GPU: shared heaps: off (%s); the geometry heaps keep a GPU copy\n", why.c_str());
    };
    if (!instance.IsDmaBufExportSupported()) {
        off("the driver cannot export memory as a dma-buf");
        return;
    }
    const auto ranges = ReadHeapFile(heap_file);
    if (ranges.empty()) {
        off("no heap ranges recorded yet; they are learned this session and shared from the next start");
        return;
    }
    const vk::BufferCreateInfo probe_ci = SparseInfo(64ull << 20, arena_usage);
    const vk::DeviceBufferMemoryRequirements requirements{.pCreateInfo = &probe_ci};
    const std::uint32_t bits =
        instance.GetDevice().getBufferMemoryRequirements(requirements).memoryRequirements.memoryTypeBits;
    memory_type = PickType(instance.GetMemoryProperties(), bits);
    if (memory_type == ~0u) {
        off("no CPU-cached, coherent memory type an arena takes");
        return;
    }
    std::string why;
    if (!Probe(arena_usage, why)) {
        off(why);
        return;
    }
    // tlg: GPU writes. Between the heaps lie the buffers the GPU writes and the game then reads
    // and writes (inFAMOUS: 0x106d150100, a fault on the game's main thread every frame that
    // waits for the GPU, 2-3 s of every 5 s). Shared, the GPU writes the game's memory itself:
    // the holes between recorded ranges (under 256 MiB) are shared too, and shared pages are
    // never read-protected; the game syncs on its fences, which then mean the GPU is done.
    const char* writes_env = std::getenv("TLG_SHARED_GPU_WRITES");
    gpu_writes = !(writes_env && writes_env[0] == '0');
    auto shared = ranges;
    if (gpu_writes) {
        std::sort(shared.begin(), shared.end());
        std::vector<std::pair<std::uint64_t, std::uint64_t>> merged;
        for (const auto& [base, size] : shared) {
            if (!merged.empty() && base <= merged.back().first + merged.back().second + MinHeapBytes) {
                auto& last = merged.back();
                last.second = std::max(last.first + last.second, base + size) - last.first;
            } else {
                merged.emplace_back(base, size);
            }
        }
        shared = std::move(merged);
    }
    std::vector<std::uint64_t> flat;
    std::uint64_t total = 0;
    for (const auto& [base, size] : shared) {
        flat.push_back(base);
        flat.push_back(size);
        total += size;
    }
    active = true;
    runtime_memory_set_share_provider(&Provide, flat.data(), shared.size());
    std::printf("GPU: shared heaps: on, %zu ranges (%llu MiB) in memory type %u; the game and the GPU "
                "use one copy (TLG_SHARED_HEAPS=0 turns it off)\n",
                shared.size(), static_cast<unsigned long long>(total >> 20), memory_type);
    std::printf("GPU: shared heaps: GPU writes %s (TLG_SHARED_GPU_WRITES)\n",
                gpu_writes ? "shared too, no read faults or readback waits there; fences wait for the GPU"
                           : "off: GPU results are copied back as before");
#endif
}

bool Active() {
    return active;
}

bool GpuWrites() {
    return active && gpu_writes;
}

const vk::ExternalMemoryBufferCreateInfo* ArenaExternal() {
    return active ? &arena_external : nullptr;
}

std::size_t Count() {
    return piece_count.load(std::memory_order_acquire);
}

Piece At(std::size_t index) {
    return pieces[index];
}

bool Overlaps(std::uint64_t start, std::uint64_t end) {
    const std::size_t count = Count();
    if (count == 0 || end <= lowest.load(std::memory_order_relaxed) ||
        start >= highest.load(std::memory_order_relaxed)) {
        return false;
    }
    for (std::size_t i = 0; i < count; ++i) {
        if (pieces[i].start < end && start < pieces[i].end) {
            return true;
        }
    }
    return false;
}

} // namespace TlgSharedHeaps
