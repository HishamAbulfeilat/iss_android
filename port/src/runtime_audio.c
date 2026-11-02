/* libSceAudioOut: native PCM FIFO on Android, SDL3 audio streams elsewhere.
 * sceAudioOutOutput returns once per
 * buffer period on a steady clock, like PS4 hardware: FMOD's output thread
 * reads 256-frame slots out of its mixer's 512-frame ring, and returning in
 * bursts (whenever the device pulls a quantum) lets it overtake the mixer and
 * play stale halves - a click every 10.7 ms. The lowest SDL queue level of
 * each window is steered to two buffers (silence prefill on underrun, ±3%
 * cadence corrections), which also absorbs device clock drift. Without an audio device (headless runs, TLG_AUDIO=none)
 * ports only follow the clock. */
#define _GNU_SOURCE
#include "runtime.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <time.h>
#ifdef __ANDROID__
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/stat.h>
#include <math.h>
#else
#include <SDL3/SDL.h>
#endif

#define PORTS 25
#define ERR_NOT_OPENED ((int32_t)0x80260001)
#define ERR_INVALID_PORT ((int32_t)0x80260003)
#define ERR_INVALID_POINTER ((int32_t)0x80260004)
#define ERR_PORT_FULL ((int32_t)0x80260005)
#define ERR_INVALID_SIZE ((int32_t)0x80260006)
#define ERR_INVALID_FORMAT ((int32_t)0x80260007)
#define ERR_INVALID_FREQ ((int32_t)0x80260008)
#define ERR_INVALID_VOLUME ((int32_t)0x80260009)
#define ERR_INVALID_TYPE ((int32_t)0x8026000A)
#define ERR_ALREADY_INIT ((int32_t)0x8026000E)
#define ERR_NOT_INIT ((int32_t)0x8026000F)
#define VOLUME_0DB 32768

typedef struct {
    int used, type, channels, is_float, frames, sample_bytes, std_layout, downmix;
    int32_t volume[8];
#ifdef __ANDROID__
    unsigned char *ring; size_t ring_cap, ring_head, ring_len;
#else
    SDL_AudioStream *stream;
#endif
    int sink;
    uint64_t next_deadline_ns; /* next return of sceAudioOutOutput */
    int64_t adjust_ns; int window_min, window_count; /* queue level control */
    uint64_t last_output_us;
    pthread_mutex_t lock;
    FILE *dump;                     /* TLG_AUDIO_DUMP: raw converted PCM per port */
    int stats;
    uint64_t stat_start_ns, stat_last_ns, stat_max_gap_ns; /* TLG_AUDIO_STATS */
    unsigned stat_starved, stat_buffers; int stat_min_queued;
} Port;
typedef struct { uint16_t output; uint8_t channel, reserved; int16_t volume; uint16_t reroute; uint64_t flag, reserved64[2]; } PortState;
_Static_assert(sizeof(PortState)==32,"AudioOut port state layout");

static pthread_mutex_t table_lock=PTHREAD_MUTEX_INITIALIZER;
static Port ports[PORTS];
static int initialized, sdl_ready=-1;
static size_t buffers_out, ports_opened;

#ifdef __ANDROID__
/* Stable locks survive slot close/reopen; Port itself is reset for the next stream. */
static pthread_mutex_t audio_port_locks[PORTS];
static pthread_once_t audio_port_locks_once=PTHREAD_ONCE_INIT;
static void init_audio_port_locks(void) {
    for (int i=0;i<PORTS;++i) pthread_mutex_init(&audio_port_locks[i],NULL);
}
static pthread_mutex_t *port_mutex(Port *p) {
    pthread_once(&audio_port_locks_once,init_audio_port_locks);
    return &audio_port_locks[p-ports];
}
#else
static pthread_mutex_t *port_mutex(Port *p) { return &p->lock; }
#endif

static uint64_t now_ns(void) { struct timespec t; clock_gettime(CLOCK_MONOTONIC,&t); return (uint64_t)t.tv_sec*1000000000u+(uint64_t)t.tv_nsec; }
static void sleep_until(uint64_t deadline) {
#ifdef _WIN32
    /* winpthreads' clock_nanosleep only takes CLOCK_REALTIME (EINVAL otherwise). */
    for (uint64_t now=now_ns(); now<deadline; now=now_ns()) compat_sleep_ns(deadline-now);
#else
    struct timespec t={(time_t)(deadline/1000000000u),(long)(deadline%1000000000u)};
    while (clock_nanosleep(CLOCK_MONOTONIC,TIMER_ABSTIME,&t,NULL)) {}
#endif
}
/* Native PCM sink adapted from Daedalus GoW3/Bloodborne-Android. Keep ISS's own
 * AudioOut/Audio3d ports, formats, volume and cadence; only replace the host sink. */
#ifdef __ANDROID__
#define MIX_FRAMES 512
static int fifo_fd=-1;
static pthread_mutex_t fifo_ring_locks[PORTS];
static pthread_once_t fifo_locks_once=PTHREAD_ONCE_INIT;
static void fifo_init_locks(void) {
    for (int i=0;i<PORTS;++i) pthread_mutex_init(&fifo_ring_locks[i],NULL);
}
static int audio_backend(void) {
    if (sdl_ready<0) {
        const char *mode=getenv("TLG_AUDIO"), *path=getenv("TLG_AUDIO_FIFO");
        sdl_ready=0;
        if ((!mode || strcmp(mode,"none")) && path && *path) {
            /* Read-write like the app's own open: never blocks, whoever came first. */
            fifo_fd=open(path,O_RDWR|O_CLOEXEC|O_NOFOLLOW);
            if (fifo_fd>=0) {
                struct stat st;
                if (fstat(fifo_fd,&st)!=0 || !S_ISFIFO(st.st_mode)) {
                    close(fifo_fd); fifo_fd=-1; errno=EINVAL;
                }
            }
            if (fifo_fd<0) fprintf(stderr,"Runtime: audio FIFO %s failed (%s)\n",path,strerror(errno));
            else sdl_ready=1;
        }
        printf("Runtime: audio backend %s\n",sdl_ready ? "fifo (s16 stereo 48 kHz)" : "timer (silent)");
    }
    return sdl_ready;
}
/* Source samples (SDL channel order: FL FR FC LFE BL BR SL SR) of one frame to stereo. */
static void frame_to_stereo(const Port *p, const unsigned char *in, int *l, int *r) {
    float v[8];
    for (int c=0;c<p->channels;++c) {
        if (p->is_float) { float x; memcpy(&x,in+c*4,4); v[c]=x*32768.0f; }
        else { int16_t x; memcpy(&x,in+c*2,2); v[c]=x; }
    }
    float fl, fr;
    if (p->channels==1) fl=fr=v[0];
    else if (p->channels==2) { fl=v[0]; fr=v[1]; }
    else {
        /* 7.1 down to stereo: centre and surrounds at -3 / -6 dB, normalised so the sum cannot clip. */
        const float n=1.0f/2.7071f;
        fl=(v[0]+0.7071f*v[2]+0.5f*v[4]+0.5f*v[6])*n; fr=(v[1]+0.7071f*v[2]+0.5f*v[5]+0.5f*v[7])*n;
    }
    if (!isfinite(fl)) fl=0;
    if (!isfinite(fr)) fr=0;
    fl=fl>32767 ? 32767 : fl<-32768 ? -32768 : fl;
    fr=fr>32767 ? 32767 : fr<-32768 ? -32768 : fr;
    *l=(int)fl; *r=(int)fr;
}
static void *mixer_thread(void *arg) {
    (void)arg;
    int16_t out[MIX_FRAMES*2];
    static int32_t acc[MIX_FRAMES*2];
    unsigned char frame[8*4];
    int reported_pcm=0;
    for (;;) {
        memset(acc,0,sizeof acc);
        pthread_mutex_lock(&table_lock);
        for (int id=0;id<PORTS;++id) {
            Port *p=&ports[id];
            if (!p->used || !p->sink) continue;
            const size_t fb=(size_t)p->channels*(size_t)p->sample_bytes;
            pthread_mutex_lock(&fifo_ring_locks[p-ports]);
            for (int f=0;f<MIX_FRAMES && p->ring_len>=fb;++f) {
                for (size_t i=0;i<fb;++i) frame[i]=p->ring[(p->ring_head+i)%p->ring_cap];
                p->ring_head=(p->ring_head+fb)%p->ring_cap; p->ring_len-=fb;
                int l, r; frame_to_stereo(p,frame,&l,&r);
                acc[2*f]+=l; acc[2*f+1]+=r;
            }
            pthread_mutex_unlock(&fifo_ring_locks[p-ports]);
        }
        pthread_mutex_unlock(&table_lock);
        int nonzero=0;
        for (int i=0;i<MIX_FRAMES*2;++i) {
            out[i]=(int16_t)(acc[i]>32767 ? 32767 : acc[i]<-32768 ? -32768 : acc[i]);
            nonzero|=out[i]!=0;
        }
        if (nonzero && !reported_pcm) {
            reported_pcm=1;
            printf("Runtime: native audio mixer produced non-silent s16 stereo 48 kHz PCM\n");
        }
        const unsigned char *at=(const unsigned char *)out; size_t left=sizeof out;
        while (left) {
            ssize_t n=write(fifo_fd,at,left);
            if (n<0 && errno==EINTR) continue;
            if (n<0) { usleep(5000); continue; }
            at+=n; left-=(size_t)n;
        }
    }
    return NULL;
}
static int sink_open(Port *p) {
    p->ring_cap=(size_t)p->frames*(size_t)p->channels*(size_t)p->sample_bytes*16;
    p->ring=malloc(p->ring_cap); p->ring_head=p->ring_len=0;
    if (!p->ring) return 0;
    pthread_once(&fifo_locks_once,fifo_init_locks);
    static int started;
    if (!started) {
        pthread_t t; pthread_attr_t a; pthread_attr_init(&a); pthread_attr_setdetachstate(&a,PTHREAD_CREATE_DETACHED);
        started=pthread_create(&t,&a,mixer_thread,NULL)==0;
        pthread_attr_destroy(&a);
        if (!started) { free(p->ring); p->ring=NULL; return 0; }
    }
    return 1;
}
static void sink_close(Port *p) {
    /* Under table_lock (audio_close), like the mixer's walk: it never touches the ring after this. */
    p->sink=0; free(p->ring); p->ring=NULL;
}
static int sink_queued(Port *p) { pthread_mutex_lock(&fifo_ring_locks[p-ports]); int n=(int)p->ring_len; pthread_mutex_unlock(&fifo_ring_locks[p-ports]); return n; }
static void sink_put(Port *p, const void *data, int bytes) {
    pthread_mutex_lock(&fifo_ring_locks[p-ports]);
    if (p->ring_len+(size_t)bytes<=p->ring_cap) { /* overflow (the app is not reading): drop */
        size_t tail=(p->ring_head+p->ring_len)%p->ring_cap;
        for (int i=0;i<bytes;++i) p->ring[(tail+(size_t)i)%p->ring_cap]=((const unsigned char *)data)[i];
        p->ring_len+=(size_t)bytes;
    }
    pthread_mutex_unlock(&fifo_ring_locks[p-ports]);
}
#else
static int audio_backend(void) {
    if (sdl_ready<0) {
        const char *mode=getenv("TLG_AUDIO");
        sdl_ready = (!mode || strcmp(mode,"none")) && SDL_InitSubSystem(SDL_INIT_AUDIO);
        printf("Runtime: audio backend %s\n", sdl_ready ? SDL_GetCurrentAudioDriver() : "timer (silent)");
    }
    return sdl_ready;
}
static int sink_open(Port *p) {
    SDL_AudioSpec device; int device_frames;
    p->downmix=p->channels==8 && SDL_GetAudioDeviceFormat(SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK,&device,&device_frames)
               && device.channels==2;
    SDL_AudioSpec spec={p->is_float ? SDL_AUDIO_F32 : SDL_AUDIO_S16, p->downmix ? 2 : p->channels, 48000};
    p->stream=SDL_OpenAudioDeviceStream(SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK,&spec,NULL,NULL);
    if (p->stream) SDL_ResumeAudioStreamDevice(p->stream);
    else fprintf(stderr,"Runtime: SDL audio stream failed (%s); port uses timer sink\n",SDL_GetError());
    return p->stream!=NULL;
}
static void sink_close(Port *p) { SDL_DestroyAudioStream(p->stream); p->stream=NULL; }
static int sink_queued(Port *p) { return SDL_GetAudioStreamQueued(p->stream); }
static void sink_put(Port *p, const void *data, int bytes) { SDL_PutAudioStreamData(p->stream,data,bytes); }
#endif
static int port_range(int type, int *first, int *last) {
    switch (type) {
    case 0: *first=0; *last=7; return 1;      /* main */
    case 1: *first=8; *last=8; return 1;      /* bgm */
    case 2: *first=9; *last=12; return 1;     /* voice */
    case 3: *first=13; *last=16; return 1;    /* personal */
    case 4: *first=17; *last=20; return 1;    /* pad speaker */
    case 126: *first=21; *last=22; return 1;  /* audio3d */
    case 127: *first=23; *last=24; return 1;  /* aux */
    default: return 0;
    }
}
/* SDL's 7.1-to-stereo matrix keeps only ~0.21 of the front channels (-13.5 dB), so the game
   was far too quiet on stereo outputs. Folds SDL-ordered FL FR C LFE BL BR SL SR frames to
   stereo in place: fronts at full level, center and surrounds at -3 dB, LFE dropped, clamped.
   Returns the stereo byte count. */
static size_t downmix_stereo(unsigned char *buf, size_t frames, int is_float) {
    const float k=0.70710678f;
    for (size_t f=0;f<frames;++f) {
        float s[8], lr[2];
        for (int c=0;c<8;++c) {
            if (is_float) memcpy(&s[c],buf+(f*8+(size_t)c)*4,4);
            else { int16_t v; memcpy(&v,buf+(f*8+(size_t)c)*2,2); s[c]=(float)v/32768.0f; }
        }
        lr[0]=s[0]+k*(s[2]+s[4]+s[6]); lr[1]=s[1]+k*(s[2]+s[5]+s[7]);
        for (int c=0;c<2;++c) {
            float v=lr[c]<-1.0f ? -1.0f : lr[c]>1.0f ? 1.0f : lr[c];
            if (is_float) memcpy(buf+(f*2+(size_t)c)*4,&v,4);
            else { int16_t o=(int16_t)(v*32767.0f); memcpy(buf+(f*2+(size_t)c)*2,&o,2); }
        }
    }
    return frames*2*(is_float ? 4u : 2u);
}
static Port *port_of(int32_t handle, int32_t *error) {
    int id=handle & 0xff;
    if ((handle & 0x3f000000)!=0x20000000 || id>=PORTS) { *error=ERR_INVALID_PORT; return NULL; }
    if (!ports[id].used) { *error=ERR_NOT_OPENED; return NULL; }
    return &ports[id];
}

static ABI int32_t audio_init(void) {
    pthread_mutex_lock(&table_lock);
    int32_t r=initialized ? ERR_ALREADY_INIT : 0;
    initialized=1;
    pthread_mutex_unlock(&table_lock);
    if (runtime_verbose()) printf("Verbose: sceAudioOutInit -> %#x\n",(unsigned)r);
    return r;
}
static int32_t open_port(int32_t type, uint32_t length, uint32_t freq, uint32_t param) {
    if (runtime_verbose() && ((!length || length>2048 || (length & 0xff)) || freq!=48000 || (param & 0xff)>7))
        printf("Verbose: sceAudioOutOpen refused: type %d, length %u, freq %u, param %#x\n",type,length,freq,param);
    if (!length || length>2048 || (length & 0xff)) return ERR_INVALID_SIZE;
    if (freq!=48000) return ERR_INVALID_FREQ;
    uint32_t format=param & 0xff;
    if (format>7) return ERR_INVALID_FORMAT;
    int first, last;
    if (!port_range(type,&first,&last)) return ERR_INVALID_TYPE;
    static const int channels[8]={1,2,8,1,2,8,8,8};
    pthread_mutex_lock(&table_lock);
    int id=-1;
    for (int i=first;i<=last;++i) if (!ports[i].used) { id=i; break; }
    if (id<0) { pthread_mutex_unlock(&table_lock); return ERR_PORT_FULL; }
    Port *p=&ports[id];
    memset(p,0,sizeof(*p));
#ifdef __ANDROID__
    (void)port_mutex(p);
#else
    pthread_mutex_init(&p->lock,NULL);
#endif
    p->used=1; p->type=type; p->channels=channels[format]; p->is_float=format>=3 && format!=6;
    p->sample_bytes=p->is_float ? 4 : 2; p->frames=(int)length; p->std_layout=format>=6;
    for (int c=0;c<8;++c) p->volume[c]=VOLUME_0DB;
    if (audio_backend()) p->sink=sink_open(p);
    if (sdl_ready && !p->sink) fprintf(stderr,"Runtime: audio sink failed for port %d\n",id);
    p->stats=getenv("TLG_AUDIO_STATS")!=NULL;
    const char *dump=getenv("TLG_AUDIO_DUMP");
    if (dump) {
        char path[4096];
        snprintf(path,sizeof(path),"%s.port%d.%dch.%s",dump,id,p->downmix ? 2 : p->channels,p->is_float ? "f32" : "s16");
        p->dump=fopen(path,"wb");
    }
    ++ports_opened;
    pthread_mutex_unlock(&table_lock);
    printf("Runtime: audio port %d opened (type %d, %d ch, %s, %u frames%s)\n",id,type,p->channels,p->is_float ? "float" : "s16",length,
           p->downmix ? ", downmixed to stereo" : "");
    if (runtime_verbose()) printf("Verbose: audio port %d: host sink %s, format code %u\n",id,p->sink ? "open" : "none (timer sink)",format);
    return (type<<16) | id | 0x20000000;
}
static ABI int32_t audio_open(int32_t user, int32_t type, int32_t index, uint32_t length, uint32_t freq, uint32_t param) {
    (void)user; (void)index;
    if (!initialized) return ERR_NOT_INIT;
    return open_port(type,length,freq,param);
}
static ABI int32_t audio_close(int32_t handle) {
    pthread_mutex_lock(&table_lock);
    int32_t error=0;
    Port *p=port_of(handle,&error);
    if (p) {
        pthread_mutex_lock(port_mutex(p));
        if (p->sink) sink_close(p);
        if (p->dump) fclose(p->dump);
        p->dump=NULL;
        p->sink=0; p->used=0;
        pthread_mutex_unlock(port_mutex(p));
    }
    pthread_mutex_unlock(&table_lock);
    return p ? 0 : error;
}
/* `pace`: wait for this port's next period. sceAudioOutOutputs waits once for all its ports. */
static int32_t output_port(int32_t handle, const void *data, int pace) {
    int32_t error=0;
    pthread_mutex_lock(&table_lock);
    Port *p=port_of(handle,&error);
    pthread_mutex_unlock(&table_lock);
    if (!p) return error;
    pthread_mutex_lock(port_mutex(p));
    if (!p->used) { pthread_mutex_unlock(port_mutex(p)); return ERR_NOT_OPENED; }
    size_t samples=(size_t)p->frames*(size_t)p->channels, bytes=samples*(size_t)p->sample_bytes;
    uint64_t period=(uint64_t)p->frames*1000000000u/48000u;
    if (data) {
        /* Apply per-channel volume and map PS4 8ch (L R C LFE SL SR BL BR) to SDL 7.1 order. */
        unsigned char converted[2048*8*4];
        static const int remap[8]={0,1,2,3,6,7,4,5};
        for (size_t f=0;f<(size_t)p->frames;++f) for (int c=0;c<p->channels;++c) {
            int target=p->channels==8 && !p->std_layout ? remap[c] : c;
            size_t from=f*(size_t)p->channels+(size_t)c, to=f*(size_t)p->channels+(size_t)target;
            float gain=(float)p->volume[c]/VOLUME_0DB;
            if (p->is_float) { float v; memcpy(&v,(const char *)data+from*4,4); v*=gain; memcpy(converted+to*4,&v,4); }
            else { int16_t v; memcpy(&v,(const char *)data+from*2,2); v=(int16_t)((float)v*gain); memcpy(converted+to*2,&v,2); }
        }
        if (p->downmix) bytes=downmix_stereo(converted,(size_t)p->frames,p->is_float);
        if (p->dump) fwrite(converted,1,bytes,p->dump);
        if (p->stats) {
            uint64_t now=now_ns();
            if (!p->stat_start_ns) { p->stat_start_ns=now; p->stat_min_queued=1<<30; }
            else if (now-p->stat_last_ns>p->stat_max_gap_ns) p->stat_max_gap_ns=now-p->stat_last_ns;
            p->stat_last_ns=now; ++p->stat_buffers;
            if (p->sink) {
                int queued=sink_queued(p);
                if (queued<p->stat_min_queued) p->stat_min_queued=queued;
                if (queued==0) ++p->stat_starved;
            }
            if (now-p->stat_start_ns>=5000000000u) {
                printf("Audio stats port %d: %u buffers/5s (expected %u), max gap %.2f ms, min queued %d bytes, starved %u\n",
                       (int)(p-ports),p->stat_buffers,(unsigned)(5u*48000u/(unsigned)p->frames),p->stat_max_gap_ns/1e6,
                       p->stat_min_queued,p->stat_starved);
                p->stat_start_ns=now; p->stat_max_gap_ns=0; p->stat_starved=0; p->stat_buffers=0; p->stat_min_queued=1<<30;
            }
        }
        if (pace) {
            uint64_t now=now_ns();
            if (!p->next_deadline_ns || now>p->next_deadline_ns+8*period) p->next_deadline_ns=now; /* start or stall: restart cadence */
            else sleep_until(p->next_deadline_ns);
            p->next_deadline_ns+=period;
        }
        if (p->sink) {
            /* The device drains the queue in quanta (21 ms on PipeWire), so the level
               is a sawtooth: its minimum over ~32 buffers is what gets controlled. */
            int low=2*(int)bytes, queued=sink_queued(p);
            if (queued<(int)bytes) {
                static const unsigned char silence[2048*8*4];
                sink_put(p,silence,low-queued<(int)bytes ? low-queued : (int)bytes);
                queued=low;
            }
            if (!p->window_count || queued<p->window_min) p->window_min=queued;
            if (++p->window_count==32) {
                p->adjust_ns=p->window_min>low+2*(int)bytes ? (int64_t)(period/32) : p->window_min<low ? -(int64_t)(period/32) : 0;
                p->window_count=0;
            }
            p->next_deadline_ns+=(uint64_t)p->adjust_ns;
            sink_put(p,converted,(int)bytes);
        }
        ++buffers_out;
    }
    p->last_output_us=now_ns()/1000;
    pthread_mutex_unlock(port_mutex(p));
    return data ? (int32_t)samples : 0;
}
static ABI int32_t audio_output(int32_t handle, const void *data) { return output_port(handle,data,1); }
typedef struct { int32_t handle; const void *data; } OutputParam;
static ABI int32_t audio_outputs(const OutputParam *params, uint32_t count) {
    if (!params) return ERR_INVALID_POINTER;
    if (!count || count>PORTS) return ERR_PORT_FULL;
    int32_t result=0;
    /* One period per call for all ports (FMOD feeds main and BGM together): pacing every
       port would halve the rate and starve both. */
    for (uint32_t i=0;i<count;++i) { int32_t r=output_port(params[i].handle,params[i].data,i==0); if (r<0) return r; result=r; }
    return result;
}
static ABI int32_t audio_volume(int32_t handle, int32_t flags, const int32_t *volume) {
    int32_t error=0;
    pthread_mutex_lock(&table_lock);
    Port *p=port_of(handle,&error);
    pthread_mutex_unlock(&table_lock);
    if (!p) return error;
    if (!volume) return ERR_INVALID_POINTER;
    pthread_mutex_lock(port_mutex(p));
    for (int c=0;c<8;++c) if (flags & (1<<c)) {
        if (volume[c]<0 || volume[c]>VOLUME_0DB) { pthread_mutex_unlock(port_mutex(p)); return ERR_INVALID_VOLUME; }
        p->volume[c]=volume[c];
    }
    pthread_mutex_unlock(port_mutex(p));
    return 0;
}
static ABI int32_t audio_state(int32_t handle, PortState *state) {
    int32_t error=0;
    pthread_mutex_lock(&table_lock);
    Port *p=port_of(handle,&error);
    pthread_mutex_unlock(&table_lock);
    if (!p) return error;
    if (!state) return ERR_INVALID_POINTER;
    memset(state,0,sizeof(*state));
    switch (p->type) {
    case 2: case 3: state->output=0x40; state->channel=1; break;          /* headphone */
    case 4: state->output=0x04; state->channel=1; state->volume=127; break; /* pad speaker */
    default: state->output=0x01; state->channel=(uint8_t)(p->channels>2 ? 2 : p->channels); break;
    }
    return 0;
}
static ABI int32_t audio_last_time(int32_t handle, uint64_t *time) {
    int32_t error=0;
    pthread_mutex_lock(&table_lock);
    Port *p=port_of(handle,&error);
    pthread_mutex_unlock(&table_lock);
    if (!p) return error;
    if (!time) return ERR_INVALID_POINTER;
    *time=p->last_output_us; return 0;
}

/* Host-side ports for other runtime libraries (Audio3d): same device, pacing and dump path as
   guest ports, without requiring the guest's sceAudioOutInit. */
int32_t runtime_audio_host_open(int32_t type, uint32_t frames, uint32_t format) { return open_port(type,frames,48000,format); }
int32_t runtime_audio_host_output(int32_t handle, const void *data, int pace) { return output_port(handle,data,pace); }
int32_t runtime_audio_host_close(int32_t handle) { return audio_close(handle); }

static const RuntimeExport exports[]={
    {"sceAudioOutInit",audio_init}, {"sceAudioOutOpen",audio_open}, {"sceAudioOutClose",audio_close},
    {"sceAudioOutOutput",audio_output}, {"sceAudioOutOutputs",audio_outputs},
    {"sceAudioOutSetVolume",audio_volume}, {"sceAudioOutGetPortState",audio_state},
    {"sceAudioOutGetLastOutputTime",audio_last_time},
};
uintptr_t runtime_audio_resolve(const char *name) { return RUNTIME_LOOKUP(exports,name); }
void runtime_audio_report(void) { printf("Runtime: audio ports opened=%zu, buffers output=%zu\n",ports_opened,buffers_out); }
