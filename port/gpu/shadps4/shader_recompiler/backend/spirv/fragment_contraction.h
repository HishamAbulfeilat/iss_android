// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include <bit>
#include <cstdint>
#include <cstdlib>

namespace Shader::Backend::SPIRV {

// Release 0.2.5 compatibility flag +9: relax contraction only for a fragment
// shader without a depth export and with at most one color export target.
// This permits driver FMA formation; it does not enable SPIR-V fast math.
inline bool FragmentContractionEnabled() {
#if defined(__ANDROID__) && defined(__BIONIC__)
    const char* value = std::getenv("TLG_FRAGMENT_CONTRACTION");
    return !value || value[0] != '0';
#else
    return false;
#endif
}

constexpr bool CanContractFragment(bool enabled, bool fragment, bool writes_depth,
                                   std::uint32_t color_targets) {
    return enabled && fragment && !writes_depth && std::popcount(color_targets) <= 1;
}

} // namespace Shader::Backend::SPIRV
