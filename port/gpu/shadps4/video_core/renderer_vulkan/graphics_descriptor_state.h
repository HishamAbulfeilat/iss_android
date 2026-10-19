// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include <algorithm>
#include <array>
#include <cstdint>
#include <span>

namespace Vulkan {

// Recording-context state of graphics set 0 in the command stream. The key
// identifies a template's lifetime, not a recyclable Vulkan handle or pointer.
class GraphicsDescriptorState {
public:
    void Invalidate() noexcept { key = 0; }

    bool Reuse(std::uint64_t next_key, std::span<const std::uint64_t> next) {
        if (!next_key || next.empty() || next.size() > words.size()) {
            Invalidate();
            return false;
        }
        if (key == next_key && count == next.size() &&
            std::equal(next.begin(), next.end(), words.begin())) {
            return true;
        }
        std::copy(next.begin(), next.end(), words.begin());
        count = next.size();
        key = next_key;
        return false;
    }

private:
    std::uint64_t key = 0;
    std::size_t count = 0;
    std::array<std::uint64_t, 256> words{};
};

} // namespace Vulkan
