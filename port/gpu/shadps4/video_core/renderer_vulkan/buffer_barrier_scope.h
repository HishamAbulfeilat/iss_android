// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include "video_core/renderer_vulkan/vk_common.h"

namespace Vulkan {

// All buffer consumers implemented by this runtime, including uses AFTER the
// next draw. FlushBarriers clears the whole hazard tracker, so narrowing to only
// the immediate consumer would lose visibility for later copies/indirect draws.
inline constexpr vk::AccessFlags2 BufferAccessScope =
    vk::AccessFlagBits2::eIndirectCommandRead | vk::AccessFlagBits2::eIndexRead |
    vk::AccessFlagBits2::eVertexAttributeRead | vk::AccessFlagBits2::eUniformRead |
    vk::AccessFlagBits2::eShaderRead | vk::AccessFlagBits2::eShaderWrite |
    vk::AccessFlagBits2::eTransferRead | vk::AccessFlagBits2::eTransferWrite;

inline vk::MemoryBarrier2 ScopeBufferBarrier(vk::MemoryBarrier2 barrier) {
#if defined(__ANDROID__) && defined(ISS_ANDROID_BIONIC)
    // Generic/unmodelled producers keep the original conservative dependency.
    // Attachment transitions have their own image barriers, never this tracker.
    if (!(barrier.srcAccessMask & ~BufferAccessScope)) {
        barrier.dstAccessMask = BufferAccessScope;
    }
#endif
    return barrier;
}

} // namespace Vulkan
