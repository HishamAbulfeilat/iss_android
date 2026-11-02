// Adapted from Daedalus GoW3 9b2f68e and Bloodborne/Bloodframe; Second Son integration.
// Iss 移植：libbloodframe.so 的 C API（change add-native-frame-generation）。
// 上游：Bloodborne-Android tools/android/lsfg/bb_lsfg.h（BB 对 libbloodframe 的宿主头），
// 仅 Bb→Iss、BB_LSFG→ISS_LSFG、bb_lsfg_→iss_lsfg_ 前缀差异；引擎谱系与许可见
// build_framegen.sh 与 THIRD_PARTY_LICENSES.txt。
//
// dlopen 符号名契约：libbloodframe.so 的导出仍为上游 bb_lsfg_* 名（引擎未改名）——
// 本头声明是宿主侧别名（iss_lsfg.cpp dlsym "bb_lsfg_*" 后存入这些槽位）。, Lossless Scaling frame generation for a native Android (Bionic)
// process (android/bb_lsfg.cpp over the framegen backend of lsfg-vk-android). Bloodframe is based on
// lsfg-vk 1.0 by PancakeTAS (MIT) via GameNative's Android fork of it.
//
// A host can dlopen() the library only when frame generation is on, so there is no link-time
// dependency. The library makes its own VkInstance/VkDevice on the same physical device as the host's,
// through the host's own vkGetInstanceProcAddr (the Vulkan driver the process loaded, e.g. a Turnip
// zip), and shares images with the host's device as AHardwareBuffers.
// Nothing here throws; failures return -1 (or NULL) and write a message into `err`.
// One initialized runtime at a time; every call is serialized inside the library.
#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

struct AHardwareBuffer;

#define ISS_LSFG_API __attribute__((visibility("default")))

typedef struct IssLsfgConfig {
    void* vk_get_instance_proc_addr; ///< PFN_vkGetInstanceProcAddr of the host's driver
    const char* dll_path;            ///< the player's Lossless.dll
    uint8_t device_uuid[16];         ///< VkPhysicalDeviceIDProperties of the host's device
    uint8_t driver_uuid[16];
    float flow_scale;                ///< user-visible, 0.25 .. 1.0 (lower: faster, coarser motion)
    int32_t performance;             ///< 1: the light model (LSFG_3_1P), 0: the quality model
    int32_t generation_count;        ///< generated frames per real one: multiplier - 1 (1 .. 3)
    int32_t shared_format;           ///< VkFormat of the shared images (37 RGBA8, 97 RGBA16F)
    int32_t hdr;                     ///< the frames are HDR (the host has SDR frames: 0)
    int32_t fp16;                    ///< 1: the half-precision SPIR-V set of the Lossless.dll when it and the
                                     ///< GPU have it; else (and for 0) its full-precision SPIR-V set; the
                                     ///< DXBC set translated only as the last resort. iss_lsfg_shader_set
                                     ///< says which runs and why.
} IssLsfgConfig;

typedef struct IssLsfgInfo {
    uint64_t input_ahb_usage;  ///< AHARDWAREBUFFER_USAGE_* the input buffers must be allocated with
    uint64_t output_ahb_usage; ///< ... the output buffers
    int32_t sync_fd_ok;        ///< the framegen device exports/imports sync files
    int32_t input_copy;        ///< the framegen device copies the input buffers (not sampled directly)
    int32_t output_copy;       ///< ... copies its result into the output buffers
} IssLsfgInfo;

typedef struct IssLsfgPresent {
    int32_t out_ready_fd[3];  ///< sync file per generated frame (-1: ready, or the host waited)
    int32_t batch_complete_fd; ///< sync file: the framegen device is done with the input buffers
    int32_t gpu_synced;        ///< 1: the fds above are the dependencies; 0: the call waited (host)
} IssLsfgPresent;

/// Starts the runtime: the framegen instance and device, the shaders from the Lossless.dll.
ISS_LSFG_API int iss_lsfg_init(const IssLsfgConfig* config, IssLsfgInfo* info, char* err, size_t err_len);

/// Makes a context over two input buffers and `out_count` output buffers (the config's
/// generation_count), all allocated with the usage iss_lsfg_init reported and `format`.
/// Returns the context id (>= 0) or -1.
ISS_LSFG_API int iss_lsfg_create_context(struct AHardwareBuffer* in0, struct AHardwareBuffer* in1,
                                       struct AHardwareBuffer* const* outs, uint32_t out_count,
                                       uint32_t width, uint32_t height, int32_t format, char* err,
                                       size_t err_len);

/// Hands the next real frame to the context. `in_sem_fd` is the sync file of the host's copy of the
/// frame into the input buffer (ownership passes to the library; -1: already complete).
/// `count` generated frames (0: only the temporal history advances). The sync files in `out` are
/// owned by the caller. Returns 0 or -1.
ISS_LSFG_API int iss_lsfg_present(int ctx, int in_sem_fd, uint32_t count, IssLsfgPresent* out, char* err,
                                size_t err_len);

/// Waits (host) for the context's last present; 0 when it completed, -1 on timeout.
ISS_LSFG_API int iss_lsfg_wait(int ctx, uint64_t timeout_ns);

ISS_LSFG_API void iss_lsfg_destroy_context(int ctx);
/// Ends the runtime (after the last context).
ISS_LSFG_API void iss_lsfg_shutdown(void);

/// The shader set the session runs: ISS_LSFG_SET_*. `reason` (when not NULL) gets a short text for the
/// log: why the sets before this one are not used, or what this one is. It can change at the first
/// iss_lsfg_create_context: the shaders are compiled there, and a driver that rejects a set makes the
/// library use the next one for the rest of the process. Returns -1 before iss_lsfg_init.
ISS_LSFG_API int iss_lsfg_shader_set(char* reason, size_t len);
#define ISS_LSFG_SET_FP16_SPIRV 0 ///< the Lossless.dll's half-precision SPIR-V (needs shaderFloat16)
#define ISS_LSFG_SET_FP32_SPIRV 1 ///< the Lossless.dll's full-precision SPIR-V
#define ISS_LSFG_SET_FP32_DXBC 2  ///< the dll's DXBC translated to SPIR-V by the library (the last resort)

/// The precision the session runs: 1 half (FP16 SPIR-V), 0 full (either full-precision set); `reason`
/// as for iss_lsfg_shader_set. Kept for hosts that only ask for the precision.
ISS_LSFG_API int iss_lsfg_precision(char* reason, size_t len);

/// One line about the backend (driver, AHB transport mode, sync path, capabilities) for the log.
ISS_LSFG_API int iss_lsfg_diagnostics(char* buf, size_t len);

#ifdef __cplusplus
}
#endif
