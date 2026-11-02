// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include <algorithm>
#include <span>
#include <vector>
#include "video_core/renderer_vulkan/vk_common.h"

namespace Vulkan {

// Producer-owned command-buffer state, like Scheduler::dynamic_state. This is only the
// vertex format: guest memory, buffer handles and offsets must still be resolved per draw.
class VertexInputState {
public:
    using Binding = vk::VertexInputBindingDescription2EXT;
    using Attribute = vk::VertexInputAttributeDescription2EXT;

    void Invalidate() noexcept { valid = false; }

    bool Matches(std::span<const Binding> new_bindings,
                 std::span<const Attribute> new_attributes) const {
        return valid && std::ranges::equal(bindings, new_bindings) &&
               std::ranges::equal(attributes, new_attributes);
    }

    void Store(std::span<const Binding> new_bindings,
               std::span<const Attribute> new_attributes) {
        Invalidate();
        // An extension chain is not an owned snapshot. Unknown chains always re-emit.
        if (std::ranges::any_of(new_bindings, [](const auto& b) { return b.pNext != nullptr; }) ||
            std::ranges::any_of(new_attributes, [](const auto& a) { return a.pNext != nullptr; })) {
            return;
        }
        bindings.assign(new_bindings.begin(), new_bindings.end());
        attributes.assign(new_attributes.begin(), new_attributes.end());
        valid = true;
    }

private:
    bool valid = false;
    std::vector<Binding> bindings;
    std::vector<Attribute> attributes;
};

} // namespace Vulkan
