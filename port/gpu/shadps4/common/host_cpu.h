// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once
#include "common/arch.h"
#include "common/rdtsc.h"
namespace Common { inline void HostPause() {
#if defined(ARCH_X86_64)
 asm volatile("pause");
#elif defined(ARCH_ARM64)
 asm volatile("yield");
#endif
} }
