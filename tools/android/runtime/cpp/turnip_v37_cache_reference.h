/* SPDX-License-Identifier: MIT
 * Copyright © 2016 Red Hat.
 * Copyright © 2016 Bas Nieuwenhuizen
 * Based in part on anv: Copyright © 2015 Intel Corporation
 *
 * Verbatim algorithm excerpts from Mesa aa35464ee50c1311eb6cebb9882d02738642b692:
 * src/freedreno/vulkan/tu_cmd_buffer.{cc,h}
 * https://github.com/chaotic-cx/mesa-mirror/tree/aa35464ee50c1311eb6cebb9882d02738642b692
 * V37 identifies this upstream base; downstream gen8 patches are not represented.
 * Test harness provides Vulkan definitions, chip enum and BITMASK_ENUM.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
enum tu_cmd_access_mask {
   TU_ACCESS_NONE = 0,
   TU_ACCESS_UCHE_READ = 1 << 0,
   TU_ACCESS_UCHE_WRITE = 1 << 1,
   TU_ACCESS_CCU_COLOR_READ = 1 << 2,
   TU_ACCESS_CCU_COLOR_WRITE = 1 << 3,
   TU_ACCESS_CCU_DEPTH_READ = 1 << 4,
   TU_ACCESS_CCU_DEPTH_WRITE = 1 << 5,

   /* Experiments have shown that while it's safe to avoid flushing the CCU
    * after each blit/renderpass, it's not safe to assume that subsequent
    * lookups with a different attachment state will hit unflushed cache
    * entries. That is, the CCU needs to be flushed and possibly invalidated
    * when accessing memory with a different attachment state. Writing to an
    * attachment under the following conditions after clearing using the
    * normal 2d engine path is known to have issues:
    *
    * - It isn't the 0'th layer.
    * - There are more than one attachment, and this isn't the 0'th attachment
    *   (this seems to also depend on the cpp of the attachments).
    *
    * Our best guess is that the layer/MRT state is used when computing
    * the location of a cache entry in CCU, to avoid conflicts. We assume that
    * any access in a renderpass after or before an access by a transfer needs
    * a flush/invalidate, and use the _INCOHERENT variants to represent access
    * by a renderpass.
    */
   TU_ACCESS_CCU_COLOR_INCOHERENT_READ = 1 << 6,
   TU_ACCESS_CCU_COLOR_INCOHERENT_WRITE = 1 << 7,
   TU_ACCESS_CCU_DEPTH_INCOHERENT_READ = 1 << 8,
   TU_ACCESS_CCU_DEPTH_INCOHERENT_WRITE = 1 << 9,

   /* Accesses which bypasses any cache. e.g. writes via the host,
    * CP_EVENT_WRITE::BLIT, and the CP are SYSMEM_WRITE.
    */
   TU_ACCESS_SYSMEM_READ = 1 << 10,
   TU_ACCESS_SYSMEM_WRITE = 1 << 11,

   /* Memory writes from the CP start in-order with draws and event writes,
    * but execute asynchronously and hence need a CP_WAIT_MEM_WRITES if read.
    */
   TU_ACCESS_CP_WRITE = 1 << 12,

   /* Descriptors are read through UCHE but are also prefetched via
    * CP_LOAD_STATE6 and the prefetched descriptors need to be invalidated
    * when they change.
    */
   TU_ACCESS_BINDLESS_DESCRIPTOR_READ = 1 << 13,

   /* A write to a GMEM attachment made by CP_EVENT_WRITE::BLIT. */
   TU_ACCESS_BLIT_WRITE_GMEM = 1 << 14,

   /* Similar to UCHE_READ, but specifically for GMEM attachment reads. */
   TU_ACCESS_UCHE_READ_GMEM = 1 << 15,

   /* The CCHE is a write-through cache which sits behind UCHE, with multiple
    * incoherent copies. Because it's write-through we only have to worry
    * about invalidating it for reads. It's invalidated by "ccinv" in the
    * shader and CP_CCHE_INVALIDATE in the command stream.
    */
   TU_ACCESS_CCHE_READ = 1 << 16,

   TU_ACCESS_RTU_READ = 1 << 17,

   /* An access through UCHE that must always be flushed/invalidated */
   TU_ACCESS_UCHE_INCOHERENT_READ = 1 << 18,
   TU_ACCESS_UCHE_INCOHERENT_WRITE = 1 << 19,

   TU_ACCESS_READ =
      TU_ACCESS_UCHE_READ |
      TU_ACCESS_CCU_COLOR_READ |
      TU_ACCESS_CCU_DEPTH_READ |
      TU_ACCESS_CCU_COLOR_INCOHERENT_READ |
      TU_ACCESS_CCU_DEPTH_INCOHERENT_READ |
      TU_ACCESS_SYSMEM_READ |
      TU_ACCESS_BINDLESS_DESCRIPTOR_READ |
      TU_ACCESS_CCHE_READ |
      TU_ACCESS_UCHE_INCOHERENT_READ,

   TU_ACCESS_WRITE =
      TU_ACCESS_UCHE_WRITE |
      TU_ACCESS_CCU_COLOR_WRITE |
      TU_ACCESS_CCU_COLOR_INCOHERENT_WRITE |
      TU_ACCESS_CCU_DEPTH_WRITE |
      TU_ACCESS_CCU_DEPTH_INCOHERENT_WRITE |
      TU_ACCESS_SYSMEM_WRITE |
      TU_ACCESS_CP_WRITE |
      TU_ACCESS_UCHE_INCOHERENT_WRITE,

   TU_ACCESS_ALL =
      TU_ACCESS_READ |
      TU_ACCESS_WRITE,
};
enum tu_cmd_flush_bits {
   TU_CMD_FLAG_CCU_CLEAN_DEPTH = 1 << 0,
   TU_CMD_FLAG_CCU_CLEAN_COLOR = 1 << 1,
   TU_CMD_FLAG_CCU_INVALIDATE_DEPTH = 1 << 2,
   TU_CMD_FLAG_CCU_INVALIDATE_COLOR = 1 << 3,
   TU_CMD_FLAG_CACHE_CLEAN = 1 << 4,
   TU_CMD_FLAG_CACHE_INVALIDATE = 1 << 5,
   TU_CMD_FLAG_CCHE_INVALIDATE = 1 << 6,
   TU_CMD_FLAG_WAIT_MEM_WRITES = 1 << 7,
   TU_CMD_FLAG_WAIT_FOR_IDLE = 1 << 8,
   TU_CMD_FLAG_WAIT_FOR_ME = 1 << 9,
   TU_CMD_FLAG_BINDLESS_DESCRIPTOR_INVALIDATE = 1 << 10,
   /* This is an unusual flush that isn't automatically executed if pending,
    * as it isn't necessary. Therefore, it's not included in ALL_FLUSH.
    */
   TU_CMD_FLAG_BLIT_CACHE_CLEAN = 1 << 11,
   TU_CMD_FLAG_RTU_INVALIDATE = 1 << 12,
   TU_CMD_FLAG_WAIT_FOR_BR = 1 << 13,
   TU_CMD_FLAG_CACHE_INVALIDATE_GMEM = 1 << 14,
   TU_CMD_FLAG_SUBPASS_SLICE_FENCE = 1 << 15,

   TU_CMD_FLAG_ALL_CLEAN =
      TU_CMD_FLAG_CCU_CLEAN_DEPTH |
      TU_CMD_FLAG_CCU_CLEAN_COLOR |
      TU_CMD_FLAG_CACHE_CLEAN |
      /* Treat the CP as a sort of "cache" which may need to be "flushed" via
       * waiting for writes to land with WAIT_FOR_MEM_WRITES.
       */
      TU_CMD_FLAG_WAIT_MEM_WRITES,

   TU_CMD_FLAG_ALL_INVALIDATE =
      TU_CMD_FLAG_CCU_INVALIDATE_DEPTH |
      TU_CMD_FLAG_CCU_INVALIDATE_COLOR |
      TU_CMD_FLAG_CACHE_INVALIDATE |
      TU_CMD_FLAG_CACHE_INVALIDATE_GMEM |
      TU_CMD_FLAG_BINDLESS_DESCRIPTOR_INVALIDATE |
      TU_CMD_FLAG_CCHE_INVALIDATE |
      /* Treat CP_WAIT_FOR_ME as a "cache" that needs to be invalidated when a
       * a command that needs CP_WAIT_FOR_ME is executed. This means we may
       * insert an extra WAIT_FOR_ME before an indirect command requiring it
       * in case there was another command before the current command buffer
       * that it needs to wait for.
       */
      TU_CMD_FLAG_WAIT_FOR_ME |
      TU_CMD_FLAG_RTU_INVALIDATE,
};
struct tu_cache_state {
   /* Caches which must be made available (flushed) eventually if there are
    * any users outside that cache domain, and caches which must be
    * invalidated eventually if there are any reads.
    */
   BITMASK_ENUM(tu_cmd_flush_bits) pending_flush_bits;
   /* Pending flushes */
   BITMASK_ENUM(tu_cmd_flush_bits) flush_bits;
   BITMASK_ENUM(tu_cmd_flush_bits) bv_flush_bits;
};
static bool
filter_read_access(VkAccessFlags2 flags, VkPipelineStageFlags2 stages,
                   VkAccessFlags2 tu_flags, VkPipelineStageFlags2 tu_stages)
{
   return (flags & (tu_flags | VK_ACCESS_2_MEMORY_READ_BIT)) &&
      (stages & (tu_stages | VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT));
}

static bool
filter_write_access(VkAccessFlags2 flags, VkPipelineStageFlags2 stages,
                    VkAccessFlags2 tu_flags, VkPipelineStageFlags2 tu_stages)
{
   return (flags & (tu_flags | VK_ACCESS_2_MEMORY_WRITE_BIT)) &&
      (stages & (tu_stages | VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT));
}

static bool
gfx_read_access(VkAccessFlags2 flags, VkPipelineStageFlags2 stages,
                VkAccessFlags2 tu_flags, VkPipelineStageFlags2 tu_stages)
{
   return filter_read_access(flags, stages, tu_flags,
                             tu_stages | VK_PIPELINE_STAGE_2_ALL_GRAPHICS_BIT);
}

static bool
gfx_write_access(VkAccessFlags2 flags, VkPipelineStageFlags2 stages,
                 VkAccessFlags2 tu_flags, VkPipelineStageFlags2 tu_stages)
{
   return filter_write_access(flags, stages, tu_flags,
                              tu_stages | VK_PIPELINE_STAGE_2_ALL_GRAPHICS_BIT);
}

static enum tu_cmd_access_mask
vk2tu_access(VkAccessFlags2 flags, VkAccessFlags3KHR flags2,
             VkPipelineStageFlags2 stages, bool image_only, bool gmem,
             bool sparse_aliasing)
{
   BITMASK_ENUM(tu_cmd_access_mask) mask = 0;

   if (gfx_read_access(flags, stages,
                       VK_ACCESS_2_INDIRECT_COMMAND_READ_BIT |
                       VK_ACCESS_2_CONDITIONAL_RENDERING_READ_BIT_EXT |
                       VK_ACCESS_2_TRANSFORM_FEEDBACK_COUNTER_READ_BIT_EXT |
                       VK_ACCESS_2_HOST_READ_BIT,
                       VK_PIPELINE_STAGE_2_DRAW_INDIRECT_BIT |
                       VK_PIPELINE_STAGE_2_CONDITIONAL_RENDERING_BIT_EXT |
                       VK_PIPELINE_STAGE_2_TRANSFORM_FEEDBACK_BIT_EXT |
                       VK_PIPELINE_STAGE_2_HOST_BIT))
      mask |= TU_ACCESS_SYSMEM_READ;

   if (gfx_write_access(flags, stages,
                        VK_ACCESS_2_TRANSFORM_FEEDBACK_COUNTER_WRITE_BIT_EXT,
                        VK_PIPELINE_STAGE_2_TRANSFORM_FEEDBACK_BIT_EXT))
      mask |= TU_ACCESS_CP_WRITE;

   if (gfx_write_access(flags, stages,
                        VK_ACCESS_2_HOST_WRITE_BIT,
                        VK_PIPELINE_STAGE_2_HOST_BIT))
      mask |= TU_ACCESS_SYSMEM_WRITE;

#define SHADER_STAGES \
   (VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT | \
    VK_PIPELINE_STAGE_2_TESSELLATION_CONTROL_SHADER_BIT | \
    VK_PIPELINE_STAGE_2_TESSELLATION_EVALUATION_SHADER_BIT | \
    VK_PIPELINE_STAGE_2_GEOMETRY_SHADER_BIT | \
    VK_PIPELINE_STAGE_2_PRE_RASTERIZATION_SHADERS_BIT | \
    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT | \
    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)


   if (gfx_read_access(flags, stages,
                       VK_ACCESS_2_INDEX_READ_BIT |
                       VK_ACCESS_2_VERTEX_ATTRIBUTE_READ_BIT |
                       VK_ACCESS_2_UNIFORM_READ_BIT |
                       VK_ACCESS_2_SHADER_READ_BIT |
                       VK_ACCESS_2_SHADER_SAMPLED_READ_BIT |
                       VK_ACCESS_2_SHADER_STORAGE_READ_BIT |
                       VK_ACCESS_2_SHADER_BINDING_TABLE_READ_BIT_KHR |
                       VK_ACCESS_2_ACCELERATION_STRUCTURE_READ_BIT_KHR,
                       VK_PIPELINE_STAGE_2_INDEX_INPUT_BIT |
                       VK_PIPELINE_STAGE_2_VERTEX_INPUT_BIT |
                       VK_PIPELINE_STAGE_2_VERTEX_ATTRIBUTE_INPUT_BIT |
                       VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR |
                       VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_COPY_BIT_KHR |
                       SHADER_STAGES)) {
      if (sparse_aliasing)
         mask |= TU_ACCESS_UCHE_INCOHERENT_READ;
      else
         mask |= TU_ACCESS_UCHE_READ;
      mask |= TU_ACCESS_CCHE_READ;
   }

   if (gfx_read_access(flags, stages,
                       VK_ACCESS_2_ACCELERATION_STRUCTURE_READ_BIT_KHR,
                       SHADER_STAGES))
       mask |= TU_ACCESS_UCHE_READ | TU_ACCESS_CCHE_READ | TU_ACCESS_RTU_READ;

   /* Reading the AS for copying involves doing CmdDispatchIndirect with the
    * copy size as a parameter, so it's read by the CP as well as a shader.
    */
   if (gfx_read_access(flags, stages,
                       VK_ACCESS_2_ACCELERATION_STRUCTURE_READ_BIT_KHR,
                       VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR |
                       VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_COPY_BIT_KHR))
       mask |= TU_ACCESS_SYSMEM_READ | TU_ACCESS_UCHE_READ |
          TU_ACCESS_CCHE_READ;


   if (gfx_read_access(flags, stages,
                       VK_ACCESS_2_INPUT_ATTACHMENT_READ_BIT,
                       SHADER_STAGES)) {
       mask |= TU_ACCESS_UCHE_READ_GMEM;
   }

   if (gfx_read_access(flags, stages,
                       VK_ACCESS_2_DESCRIPTOR_BUFFER_READ_BIT_EXT,
                       SHADER_STAGES)) {
      if (sparse_aliasing)
         mask |= TU_ACCESS_UCHE_INCOHERENT_READ;
      else
         mask |= TU_ACCESS_UCHE_READ;
      mask |= TU_ACCESS_BINDLESS_DESCRIPTOR_READ |
              TU_ACCESS_CCHE_READ;
   }

   if (gfx_write_access(flags, stages,
                        VK_ACCESS_2_SHADER_WRITE_BIT |
                        VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT |
                        VK_ACCESS_2_TRANSFORM_FEEDBACK_WRITE_BIT_EXT,
                        VK_PIPELINE_STAGE_2_TRANSFORM_FEEDBACK_BIT_EXT |
                        SHADER_STAGES)) {
      if (sparse_aliasing)
         mask |= TU_ACCESS_UCHE_INCOHERENT_WRITE;
      else
         mask |= TU_ACCESS_UCHE_WRITE;
   }

   if (gfx_write_access(flags, stages,
                        VK_ACCESS_2_ACCELERATION_STRUCTURE_WRITE_BIT_KHR,
                        VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR))
       mask |= TU_ACCESS_UCHE_WRITE | TU_ACCESS_CP_WRITE;

   /* When using GMEM, the CCU is always flushed automatically to GMEM, and
    * then GMEM is flushed to sysmem. Furthermore, we already had to flush any
    * previous writes in sysmem mode when transitioning to GMEM. Therefore we
    * can ignore CCU and pretend that color attachments and transfers use
    * sysmem directly.
    */

   if (gfx_read_access(flags, stages,
                       VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT |
                       VK_ACCESS_2_COLOR_ATTACHMENT_READ_NONCOHERENT_BIT_EXT,
                       VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)) {
      if (gmem)
         mask |= TU_ACCESS_SYSMEM_READ;
      else
         mask |= TU_ACCESS_CCU_COLOR_INCOHERENT_READ;
   }

   if (gfx_read_access(flags, stages,
                       VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT,
                       VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT |
                       VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT)) {
      if (gmem)
         mask |= TU_ACCESS_SYSMEM_READ;
      else
         mask |= TU_ACCESS_CCU_DEPTH_INCOHERENT_READ;
   }

   if (gfx_write_access(flags, stages,
                        VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                        VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)) {
      if (gmem) {
         mask |= TU_ACCESS_SYSMEM_WRITE;
      } else {
         mask |= TU_ACCESS_CCU_COLOR_INCOHERENT_WRITE;
      }
   }

   if (gfx_write_access(flags, stages,
                        VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                        VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT |
                        VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT)) {
      if (gmem) {
         mask |= TU_ACCESS_SYSMEM_WRITE;
      } else {
         mask |= TU_ACCESS_CCU_DEPTH_INCOHERENT_WRITE;
      }
   }

   if (filter_write_access(flags, stages,
                           VK_ACCESS_2_TRANSFER_WRITE_BIT,
                           VK_PIPELINE_STAGE_2_COPY_BIT |
                           VK_PIPELINE_STAGE_2_BLIT_BIT |
                           VK_PIPELINE_STAGE_2_CLEAR_BIT |
                           VK_PIPELINE_STAGE_2_RESOLVE_BIT |
                           VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT)) {
      if (gmem) {
         mask |= TU_ACCESS_SYSMEM_WRITE;
      } else if (image_only && !sparse_aliasing) {
         /* Because we always split up blits/copies of images involving
          * multiple layers, we always access each layer in the same way, with
          * the same base address, same format, etc. This means we can avoid
          * flushing between multiple writes to the same image. This elides
          * flushes between e.g. multiple blits to the same image.
          */
         mask |= TU_ACCESS_CCU_COLOR_WRITE;
      } else {
         mask |= TU_ACCESS_CCU_COLOR_INCOHERENT_WRITE;
      }
   }

   if (filter_read_access(flags, stages,
                          VK_ACCESS_2_TRANSFER_READ_BIT,
                          VK_PIPELINE_STAGE_2_COPY_BIT |
                          VK_PIPELINE_STAGE_2_BLIT_BIT |
                          VK_PIPELINE_STAGE_2_RESOLVE_BIT |
                          VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT)) {
      if (sparse_aliasing)
         mask |= TU_ACCESS_UCHE_INCOHERENT_READ;
      else
         mask |= TU_ACCESS_UCHE_READ;
      mask |= TU_ACCESS_CCHE_READ;
   }

   return mask;
}

template <chip CHIP>
static void
tu_flush_for_access(struct tu_cache_state *cache,
                    enum tu_cmd_access_mask src_mask,
                    enum tu_cmd_access_mask dst_mask)
{
   BITMASK_ENUM(tu_cmd_flush_bits) flush_bits = 0;

   if (src_mask & TU_ACCESS_SYSMEM_WRITE) {
      cache->pending_flush_bits |= TU_CMD_FLAG_ALL_INVALIDATE;
   }

   if (src_mask & TU_ACCESS_CP_WRITE) {
      /* Flush the CP write queue.
       */
      cache->pending_flush_bits |=
         TU_CMD_FLAG_WAIT_MEM_WRITES |
         TU_CMD_FLAG_ALL_INVALIDATE;
   }

#define SRC_FLUSH(domain, clean, invalidate) \
   if (src_mask & TU_ACCESS_##domain##_WRITE) {                      \
      cache->pending_flush_bits |= TU_CMD_FLAG_##clean |             \
         (TU_CMD_FLAG_ALL_INVALIDATE & ~TU_CMD_FLAG_##invalidate);   \
   }

   SRC_FLUSH(UCHE, CACHE_CLEAN, CACHE_INVALIDATE)
   SRC_FLUSH(CCU_COLOR, CCU_CLEAN_COLOR, CCU_INVALIDATE_COLOR)
   SRC_FLUSH(CCU_DEPTH, CCU_CLEAN_DEPTH, CCU_INVALIDATE_DEPTH)

#undef SRC_FLUSH

#define SRC_INCOHERENT_FLUSH(domain, clean, invalidate)              \
   if (src_mask & TU_ACCESS_##domain##_INCOHERENT_WRITE) {           \
      flush_bits |= TU_CMD_FLAG_##clean;                             \
      cache->pending_flush_bits |=                                   \
         (TU_CMD_FLAG_ALL_INVALIDATE & ~TU_CMD_FLAG_##invalidate);   \
   }

   SRC_INCOHERENT_FLUSH(CCU_COLOR, CCU_CLEAN_COLOR, CCU_INVALIDATE_COLOR)
   SRC_INCOHERENT_FLUSH(CCU_DEPTH, CCU_CLEAN_DEPTH, CCU_INVALIDATE_DEPTH)
   SRC_INCOHERENT_FLUSH(UCHE, CACHE_CLEAN, CACHE_INVALIDATE)

#undef SRC_INCOHERENT_FLUSH

   /* Treat host & sysmem write accesses the same, since the kernel implicitly
    * drains the queue before signalling completion to the host.
    */
   if (dst_mask & (TU_ACCESS_SYSMEM_READ | TU_ACCESS_SYSMEM_WRITE)) {
      flush_bits |= cache->pending_flush_bits & TU_CMD_FLAG_ALL_CLEAN;
   }

#define DST_FLUSH(domain, clean, invalidate) \
   if (dst_mask & (TU_ACCESS_##domain##_READ |                 \
                   TU_ACCESS_##domain##_WRITE)) {              \
      flush_bits |= cache->pending_flush_bits &                \
         (TU_CMD_FLAG_##invalidate |                           \
          (TU_CMD_FLAG_ALL_CLEAN & ~TU_CMD_FLAG_##clean));     \
   }

   DST_FLUSH(UCHE, CACHE_CLEAN, CACHE_INVALIDATE)
   DST_FLUSH(CCU_COLOR, CCU_CLEAN_COLOR, CCU_INVALIDATE_COLOR)
   DST_FLUSH(CCU_DEPTH, CCU_CLEAN_DEPTH, CCU_INVALIDATE_DEPTH)

#undef DST_FLUSH

#define DST_INCOHERENT_FLUSH(domain, flush, invalidate) \
   if (dst_mask & (TU_ACCESS_##domain##_INCOHERENT_READ |      \
                   TU_ACCESS_##domain##_INCOHERENT_WRITE)) {   \
      flush_bits |= TU_CMD_FLAG_##invalidate |                 \
          (cache->pending_flush_bits &                         \
           (TU_CMD_FLAG_ALL_CLEAN & ~TU_CMD_FLAG_##flush));    \
   }

   DST_INCOHERENT_FLUSH(CCU_COLOR, CCU_CLEAN_COLOR, CCU_INVALIDATE_COLOR)
   DST_INCOHERENT_FLUSH(CCU_DEPTH, CCU_CLEAN_DEPTH, CCU_INVALIDATE_DEPTH)
   DST_INCOHERENT_FLUSH(UCHE, CACHE_CLEAN, CACHE_INVALIDATE)

   if (dst_mask & TU_ACCESS_BINDLESS_DESCRIPTOR_READ) {
      flush_bits |= TU_CMD_FLAG_BINDLESS_DESCRIPTOR_INVALIDATE;
   }

   /* There are multiple incoherent copies of CCHE, so any read through it may
    * require invalidating it and we cannot optimize away invalidates.
    */
   if (dst_mask & TU_ACCESS_CCHE_READ) {
      flush_bits |= TU_CMD_FLAG_CCHE_INVALIDATE;
   }

   if (dst_mask & TU_ACCESS_UCHE_READ_GMEM) {
      /* On a7xx+, use SUBPASS_FENCE (SUBPASS_SLICE_FENCE on a8xx) which
       * guarantees accesses for GMEM accesses through UCHE instead of
       * invalidating all of UCHE.
       */
      if (CHIP >= A7XX) {
         flush_bits |= TU_CMD_FLAG_CACHE_INVALIDATE_GMEM;
      } else {
         flush_bits |= TU_CMD_FLAG_CACHE_INVALIDATE;
      }
   }

   /* The blit cache is a special case dependency between CP_EVENT_WRITE::BLIT
    * (from GMEM loads/clears) to any GMEM attachment reads done via the UCHE
    * (Eg: Input attachments/CP_BLIT) which needs an explicit BLIT_CACHE_CLEAN
    * for the event blit writes to land, it has the following properties:
    * - Set on reads rather than on writes, like flushes.
    * - Not executed automatically if pending, like invalidates.
    * - Pending bits passed through to secondary command buffers, if they're
    *   continuing the render pass.
    */
   if (src_mask & TU_ACCESS_BLIT_WRITE_GMEM) {
      cache->pending_flush_bits |= TU_CMD_FLAG_BLIT_CACHE_CLEAN;
   }

   if ((dst_mask & TU_ACCESS_UCHE_READ_GMEM) &&
       (cache->pending_flush_bits & TU_CMD_FLAG_BLIT_CACHE_CLEAN)) {
      flush_bits |= TU_CMD_FLAG_BLIT_CACHE_CLEAN;
   }

   /* Nothing writes through the RTU cache so there's no point trying to
    * optimize this. Just always invalidate.
    */
   if (dst_mask & TU_ACCESS_RTU_READ)
      flush_bits |= TU_CMD_FLAG_RTU_INVALIDATE;

#undef DST_INCOHERENT_FLUSH

   cache->flush_bits |= flush_bits;
   cache->pending_flush_bits &= ~flush_bits;
}
