// SPDX-License-Identifier: GPL-2.0-or-later
// Android Bionic integrates native Vulkan FSR 3.1 only.
// Keep the full temporal-upscaler ABI without linking FSR4 models or Windows providers.
#include "video_core/renderer_vulkan/vk_fsr4.h"

#ifdef ISS_ANDROID_BIONIC
namespace Vulkan {
struct Fsr4Upscaler::Impl {};
Fsr4Upscaler::Fsr4Upscaler(const Instance&, Scheduler&)
    : impl{std::make_unique<Impl>()} {}
Fsr4Upscaler::~Fsr4Upscaler() = default;
bool Fsr4Upscaler::Record(const Frame&) { return false; }
const char* Fsr4Upscaler::Problem() const noexcept {
    return "FSR 4 is not available on Android Bionic (FSR 3.1 only)";
}
bool Fsr4Upscaler::Fatal() const noexcept { return true; }
} // namespace Vulkan
#endif
