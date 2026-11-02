#include <cassert>
#include <cstdio>
#include "../../../../port/gpu/shim/tlg_android_memory.h"

int main() {
    constexpr std::uint64_t MiB = 1024 * 1024;
    const TlgAndroidMemory::Snapshot ram_low{1024 * 1000, 1024 * 4096, 1024 * 3000};
    assert(ram_low.UnderPressure(1500 * MiB));
    const TlgAndroidMemory::Snapshot swap_low{1024 * 3000, 1024 * 4096, 1024 * 100};
    assert(swap_low.UnderPressure(1500 * MiB));
    assert(swap_low.HeadroomBytes() == 100 * MiB);
    const TlgAndroidMemory::Snapshot swap_empty{1024 * 3000, 1024 * 4096, 0};
    assert(swap_empty.UnderPressure(1500 * MiB));
    const TlgAndroidMemory::Snapshot no_swap{1024 * 3000, 0, 0};
    assert(!no_swap.UnderPressure(1500 * MiB));
    assert(no_swap.HeadroomBytes() == 3000 * MiB);
    assert(!TlgAndroidMemory::Snapshot{}.UnderPressure(1500 * MiB));
    const TlgAndroidMemory::Snapshot boundary{1024 * 1500, 1024 * 4096, 1024 * 2000};
    assert(!boundary.UnderPressure(1500 * MiB));

    std::FILE* file = std::tmpfile();
    assert(file);
    std::fputs("MemTotal: 11379712 kB\nMemAvailable: 3072000 kB\n"
               "SwapTotal: 4194300 kB\nSwapFree: 102400 kB\n", file);
    std::rewind(file);
    const auto parsed = TlgAndroidMemory::Parse(file);
    std::fclose(file);
    assert(parsed.available_kb == 3072000);
    assert(parsed.swap_total_kb == 4194300);
    assert(parsed.swap_free_kb == 102400);
    assert(parsed.UnderPressure(1500 * MiB));
}
