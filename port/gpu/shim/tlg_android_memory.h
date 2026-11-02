#pragma once

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>

namespace TlgAndroidMemory {

struct Snapshot {
    std::uint64_t available_kb{};
    std::uint64_t swap_total_kb{};
    std::uint64_t swap_free_kb{};

    std::uint64_t HeadroomBytes() const {
        if (available_kb == 0) {
            return 0;
        }
        return (swap_total_kb ? std::min(available_kb, swap_free_kb) : available_kb) << 10;
    }

    bool UnderPressure(std::uint64_t reserve_bytes) const {
        return available_kb != 0 && HeadroomBytes() < reserve_bytes;
    }
};

inline Snapshot Parse(std::FILE* file) {
    Snapshot result;
    char line[128];
    unsigned long long kb = 0;
    while (std::fgets(line, sizeof(line), file)) {
        if (std::sscanf(line, "MemAvailable: %llu kB", &kb) == 1) {
            result.available_kb = kb;
        } else if (std::sscanf(line, "SwapTotal: %llu kB", &kb) == 1) {
            result.swap_total_kb = kb;
        } else if (std::sscanf(line, "SwapFree: %llu kB", &kb) == 1) {
            result.swap_free_kb = kb;
        }
    }
    return result;
}

#ifdef __ANDROID__
inline Snapshot Current() {
    // Both collectors use this snapshot; do not race their cached /proc reads.
    thread_local std::chrono::steady_clock::time_point last{};
    thread_local Snapshot snapshot{};
    const auto now = std::chrono::steady_clock::now();
    if (now - last >= std::chrono::milliseconds(250)) {
        last = now;
        if (std::FILE* file = std::fopen("/proc/meminfo", "r")) {
            snapshot = Parse(file);
            std::fclose(file);
        }
    }
    return snapshot;
}
#endif

} // namespace TlgAndroidMemory
