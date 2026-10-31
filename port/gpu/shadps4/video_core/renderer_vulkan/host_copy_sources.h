// SPDX-License-Identifier: GPL-2.0-or-later
// Conservative source tracking adapted from Bloodborne-Android 9271e86, vk_scheduler.cpp.
#pragma once

#include <array>
#include <cstdint>
#include <limits>
#include <mutex>

namespace Vulkan {

// False positives wait for all copies. Collisions, oversized ranges and arithmetic overflow
// must never allow a guest write to overtake a copy that still reads its old bytes.
class HostCopySources {
public:
    static constexpr std::uint64_t GranuleShift = 16;
    static constexpr std::uint64_t MaxGranules = 64;
    static constexpr std::uint32_t Slots = 4096;
    static constexpr std::uint64_t Wildcard = std::numeric_limits<std::uint64_t>::max();

    static constexpr std::uint32_t SlotOf(std::uint64_t granule) {
        return ((granule * 0x9e3779b97f4a7c15ull) >> 40) % Slots;
    }

    void Note(std::uint64_t address, std::uint64_t size) {
        if (!size) return;
        std::scoped_lock lock{mutex};
        const auto seq = ++issued;
        std::uint64_t first{}, last{};
        if (!Bounds(address, size, first, last)) {
            big = seq;
            return;
        }
        for (auto g = first; g <= last; ++g) {
            auto& slot = slots[SlotOf(g)];
            if (slot.seq > completed && slot.granule != g) {
                slot.granule = Wildcard;
            } else if (slot.granule != Wildcard || slot.seq <= completed) {
                slot.granule = g;
            }
            slot.seq = seq;
        }
    }

    bool Overlaps(std::uint64_t address, std::uint64_t size) {
        if (!size) return false;
        std::scoped_lock lock{mutex};
        if (big > completed) return true;
        std::uint64_t first{}, last{};
        if (!Bounds(address, size, first, last)) return true;
        for (auto g = first; g <= last; ++g) {
            const auto& slot = slots[SlotOf(g)];
            if (slot.seq > completed && (slot.granule == g || slot.granule == Wildcard)) {
                return true;
            }
        }
        return false;
    }

    std::uint64_t Snapshot() {
        std::scoped_lock lock{mutex};
        return issued;
    }

    void RetireThrough(std::uint64_t seq) {
        std::scoped_lock lock{mutex};
        if (seq > completed) completed = seq;
    }

private:
    static bool Bounds(std::uint64_t address, std::uint64_t size,
                       std::uint64_t& first, std::uint64_t& last) {
        if (size - 1 > Wildcard - address) return false;
        first = address >> GranuleShift;
        last = (address + size - 1) >> GranuleShift;
        return last - first < MaxGranules;
    }
    struct Slot { std::uint64_t granule{}, seq{}; };
    std::mutex mutex;
    std::array<Slot, Slots> slots{};
    std::uint64_t issued{}, completed{}, big{};
};
} // namespace Vulkan
