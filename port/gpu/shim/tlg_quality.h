// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

namespace TlgQuality {

inline constexpr int MaxLevel = 14;

class Governor {
public:
    int Update(int target_fps, double fps) {
        if (target_fps <= 0) {
            *this = Governor{};
            return 0;
        }
        if (fps <= 0.0) {
            slow_ = fast_ = 0;
            return level_;
        }
        --raise_cooldown_;
        --lower_cooldown_;
        if (fps < target_fps * 0.93) {
            ++slow_;
            fast_ = 0;
        } else if (fps > target_fps * 1.30) {
            ++fast_;
            slow_ = 0;
        } else {
            slow_ = fast_ = 0;
        }
        if (slow_ >= 2 && raise_cooldown_ <= 0) {
            const int step = fps < target_fps * 0.75 ? 3 : 1;
            level_ = level_ + step < MaxLevel ? level_ + step : MaxLevel;
            raise_cooldown_ = 3;
            lower_cooldown_ = 6;
            slow_ = 0;
        } else if (fast_ >= 8 && lower_cooldown_ <= 0 && level_ > 0) {
            --level_;
            lower_cooldown_ = 6;
            fast_ = 0;
        }
        return level_;
    }

    int Level() const { return level_; }

private:
    int level_ = 0;
    int slow_ = 0;
    int fast_ = 0;
    int raise_cooldown_ = 0;
    int lower_cooldown_ = 0;
};

} // namespace TlgQuality
