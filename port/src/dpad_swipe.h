/* Slow, incremental Android touchpad gestures. Retain position between taps;
 * held directions lift/reposition at an edge so long actions never stall. */
#pragma once
#include <stdint.h>
typedef struct { int active,initialized; uint8_t id; uint64_t last,gap_until; float x,y; } IssDpadSwipe;
static inline int iss_dpad_swipe_step(IssDpadSwipe *s,unsigned directions,uint64_t now) {
    const int x=!!(directions&8)-!!(directions&4);
    const int y=!!(directions&2)-!!(directions&1);
    if (!x && !y) { s->active=0; s->gap_until=0; s->last=now; return 0; }
    if (!s->initialized) { s->initialized=1; s->x=959.5f; s->y=471.f; }
    if (s->gap_until && now<s->gap_until) { s->last=now; return 0; }
    if (!s->active) {
        if(s->gap_until) { if(x)s->x=x>0?0.f:1919.f; if(y)s->y=y>0?0.f:942.f; }
        s->gap_until=0;s->active=1;s->id=(s->id+1)&127;s->last=now;return 1;
    }
    uint64_t elapsed=now>=s->last?now-s->last:0;
    if(elapsed>50000)elapsed=50000;
    const float step=(float)elapsed/1000000.f;
    s->last=now;
    /* A full sweep takes about 3.2 seconds; short presses move only a little. */
    const int at_edge=(x>0&&s->x>=1919)||(x<0&&s->x<=0)||(y>0&&s->y>=942)||(y<0&&s->y<=0);
    if(at_edge){s->active=0;s->gap_until=now+60000;return 0;}
    s->x+=x*600.f*step;s->y+=y*300.f*step;
    if(s->x<0)s->x=0;if(s->x>1919)s->x=1919;
    if(s->y<0)s->y=0;if(s->y>942)s->y=942;
    return 1;
}
