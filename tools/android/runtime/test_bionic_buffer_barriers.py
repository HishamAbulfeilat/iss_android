"""Production barriers plus the cache algorithms from V37's pinned Mesa base."""
import test_bionic_mapped_readback as mapped
from test_bionic_mapped_readback import GPU, ROOT
from test_bionic_resource_reuse import method
import unittest


@unittest.skipUnless(mapped.CXX, "C++ compiler unavailable")
class BufferBarrierTests(unittest.TestCase):
    vulkan_include = mapped.MappedReadbackTests.vulkan_include
    compile_run = mapped.MappedReadbackTests.compile_run

    def test_buffer_scope_and_turnip_cache_operations(self):
        include = self.vulkan_include()
        renderer = GPU / "video_core/renderer_vulkan"
        code = (ROOT / "tools/android/runtime/cpp/buffer_barriers.cpp").read_text()
        code = code.replace("// PRODUCTION_FLUSH", method(
            (renderer / "vk_runtime.cpp").read_text(), "void Runtime::FlushBarriers()"))
        code = code.replace("// PRODUCTION_REQUIRE", method(
            (renderer / "vk_scheduler.h").read_text(), "void RequireHostReadBarrier("))
        code = code.replace("// PRODUCTION_HOST_FLUSH", method(
            (renderer / "vk_scheduler.cpp").read_text(), "void Scheduler::FlushHostReadBarrier()"))
        reference = (ROOT / "tools/android/runtime/cpp/turnip_v37_cache_reference.h").read_text()
        # Mesa's BITMASK_ENUM annotation permits this return conversion. Our
        # standalone harness uses unsigned masks and makes the conversion explicit.
        reference = reference.replace("return mask;", "return static_cast<tu_cmd_access_mask>(mask);")
        code = code.replace("// TURNIP_REFERENCE", reference)
        self.compile_run(code, ("-I", include, "-D__ANDROID__=1", "-DISS_ANDROID_BIONIC=1"), ((),))
        self.compile_run(code, ("-I", include), ((),))


if __name__ == "__main__":
    unittest.main()
