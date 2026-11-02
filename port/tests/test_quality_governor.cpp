// SPDX-License-Identifier: GPL-2.0-or-later
#include "../gpu/shim/tlg_quality.h"

#include <cstdio>

int main() {
    TlgQuality::Governor governor;
    for (int i = 0; i < 20; ++i) {
        if (governor.Update(60, 0.0) != 0) return 1;
    }
    if (governor.Update(0, 20.0) != 0) return 2;
    if (governor.Update(60, 45.0) != 0 || governor.Update(60, 45.0) != 1) return 3;
    for (int i = 0; i < 120; ++i) governor.Update(60, 20.0);
    if (governor.Level() != TlgQuality::MaxLevel) return 4;
    for (int i = 0; i < 90; ++i) governor.Update(60, 120.0);
    if (governor.Level() >= TlgQuality::MaxLevel || governor.Level() < 0) return 5;
    std::puts("quality governor: passed");
    return 0;
}
