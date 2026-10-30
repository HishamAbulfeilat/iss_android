// SPDX-License-Identifier: GPL-2.0-or-later
// tlg: shared heaps (TLG_SHARED_HEAPS=1, Android). inFAMOUS binds two geometry heaps whole and
// read-only every frame (0.5 and 2.5 GB, heap_binds.txt): the buffer cache kept a GPU copy of all of
// them next to the game's own, about 2.9 GB of RAM twice, and write-protected their pages to find
// what to upload (the slow faults of the game's threads). Here the GPU allocates the memory under
// those ranges itself and exports it as dma-bufs; the runtime maps them as the game's memory
// (runtime_memory_set_share_provider), and the buffer cache binds the same memory as the arena's
// residency for those ranges. One copy: no uploads and no write tracking there. The idea and the
// start-up probes follow Bloodborne-Android's unified memory (ducvd89, bbport_guest_memory.cpp).
//
// Off: nothing changes. On, the start-up probe must pass (CPU and GPU see each other's writes
// through a sparse-bound exported buffer), else it logs why and stays off. Ranges come from the
// previous sessions' heap_binds.txt, so the very first session after the cache is cleared is copy
// mode.
#pragma once

#include <cstddef>
#include <cstdint>
#include <filesystem>

#include "video_core/renderer_vulkan/vk_common.h"

namespace Vulkan {
class Instance;
}

namespace TlgSharedHeaps {

/// A guest range [start, end) whose memory is `memory` (offset 0 at start).
struct Piece {
    std::uint64_t start;
    std::uint64_t end;
    vk::DeviceMemory memory;
};

/// Decides once, from the buffer cache's constructor, before any arena exists. `block_size` is the
/// arena's sparse block; `arena_usage` its buffer usage.
void Init(const Vulkan::Instance& instance, std::uint64_t block_size, vk::BufferUsageFlags arena_usage,
          const std::filesystem::path& heap_file);

/// True when shared heaps are on (probe passed and the runtime has the ranges).
bool Active();

/// What sparse arenas must chain into their create info when Active() (null otherwise): memory
/// exported as a dma-buf may only be bound to buffers created for that handle type.
const vk::ExternalMemoryBufferCreateInfo* ArenaExternal();

/// The pieces made so far (grows while the game maps memory, never shrinks). Safe from any thread.
std::size_t Count();
Piece At(std::size_t index);

/// True when [start, end) touches a shared piece.
bool Overlaps(std::uint64_t start, std::uint64_t end);

/// Calls f(lo, hi) for each part of [start, end) outside every shared piece, in order.
template <typename F>
void ForEachOutside(std::uint64_t start, std::uint64_t end, F&& f) {
    const std::size_t count = Count();
    if (count == 0 || !Overlaps(start, end)) {
        f(start, end);
        return;
    }
    std::uint64_t at = start;
    while (at < end) {
        std::uint64_t next_start = end; // the first piece starting after `at`
        bool inside = false;
        for (std::size_t i = 0; i < count; ++i) {
            const Piece piece = At(i);
            if (piece.start <= at && at < piece.end) {
                at = piece.end < end ? piece.end : end;
                inside = true;
                break;
            }
            if (piece.start > at && piece.start < next_start) {
                next_start = piece.start;
            }
        }
        if (!inside) {
            f(at, next_start);
            at = next_start;
        }
    }
}

} // namespace TlgSharedHeaps
