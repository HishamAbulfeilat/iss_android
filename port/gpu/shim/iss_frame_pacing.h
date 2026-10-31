#pragma once
#include <algorithm>
namespace IssFramePacing {
// Keep the guest's 0/1 flip divider intact, but clock it at the requested source rate.
// 30 FPS still uses a 60 Hz VBlank; 120/240 must not remain on the desktop 60 Hz default.
inline int VblankHz(int fps, int flip_rate) {
    if(fps!=30 && fps!=60 && fps!=120 && fps!=240)return 60;
    return std::max(60,fps*(std::clamp(flip_rate,0,2)+1));
}
}
