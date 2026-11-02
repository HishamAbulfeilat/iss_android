// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <span>
#include <vector>

namespace Vulkan {

enum class DescriptorPayload { Buffer, Image, Texel };

// Immutable once a pipeline becomes visible to recording/compilation workers.
// Offsets refer to copied payloads, never to producer-owned descriptor pointers.
struct DescriptorTemplateData {
    struct Slot {
        std::uint32_t binding, count, type;
        DescriptorPayload payload;
        std::size_t offset, stride;
    };
    static constexpr std::size_t MaxBytes = 64 * 1024;
    std::vector<Slot> slots;
    std::size_t bytes{};

    bool Add(std::uint32_t binding, std::uint32_t count, std::uint32_t type,
             DescriptorPayload payload, std::size_t stride, std::size_t alignment) {
        if (!count || !stride || !alignment || alignment > alignof(std::uint64_t) ||
            (alignment & (alignment - 1)) || (!slots.empty() && binding <= slots.back().binding)) {
            return false;
        }
        const auto offset = (bytes + alignment - 1) & ~(alignment - 1);
        if (offset > MaxBytes || count > (MaxBytes - offset) / stride) {
            return false;
        }
        slots.push_back({binding, count, type, payload, offset, stride});
        bytes = offset + count * stride;
        return true;
    }

    template <typename Writes>
    bool Pack(const Writes& writes, std::span<std::uint64_t> words) const {
        if (slots.empty() || writes.size() != slots.size() || words.size_bytes() < bytes) {
            return false;
        }
        auto* dst = reinterpret_cast<std::byte*>(words.data());
        for (std::size_t i = 0; i < slots.size(); ++i) {
            const auto& slot = slots[i];
            const auto& write = writes[i];
            if (write.pNext || write.dstBinding != slot.binding || write.dstArrayElement != 0 ||
                write.descriptorCount != slot.count || std::uint32_t(write.descriptorType) != slot.type) {
                return false;
            }
            const void* src = nullptr;
            std::size_t stride = 0;
            switch (slot.payload) {
            case DescriptorPayload::Buffer:
                src = write.pBufferInfo;
                stride = sizeof(*write.pBufferInfo);
                break;
            case DescriptorPayload::Image:
                src = write.pImageInfo;
                stride = sizeof(*write.pImageInfo);
                break;
            case DescriptorPayload::Texel:
                src = write.pTexelBufferView;
                stride = sizeof(*write.pTexelBufferView);
                break;
            }
            if (!src || stride != slot.stride) {
                return false;
            }
            std::memcpy(dst + slot.offset, src, slot.count * slot.stride);
        }
        return true;
    }
};

} // namespace Vulkan
