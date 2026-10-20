/* Incremental touchpad gesture for Android D-pad sources, physical and virtual. */
#pragma once
#include <stdint.h>
typedef struct { int active; uint8_t id; uint64_t last; float x,y; } IssDpadSwipe;
static inline int iss_dpad_swipe_step(IssDpadSwipe *s,unsigned directions,uint64_t now) {
    const int x=!!(directions&8)-!!(directions&4);
    const int y=!!(directions&2)-!!(directions&1);
    if (!x && !y) { s->active=0; s->last=now; return 0; }
    if (!s->active) { s->active=1; ++s->id; s->x=959.5f; s->y=471.f; s->last=now; return 1; }
    uint64_t elapsed=now>=s->last?now-s->last:0;
    if(elapsed>50000)elapsed=50000;
    const float step=(float)elapsed/1000000.f;
    s->last=now; s->x+=x*2400.f*step; s->y+=y*1200.f*step;
    if(s->x<0)s->x=0;if(s->x>1919)s->x=1919;
    if(s->y<0)s->y=0;if(s->y>942)s->y=942;
    return 1;
}
