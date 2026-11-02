"""Differential production specialization refresh and allocation-free fetch cache hits.

The decoder/resource mocks supply inputs; both old and new specialization bodies
run against the same inputs. This is a CPU allocation check, not an FPS benchmark.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_bionic_resource_reuse import method

ROOT = Path(__file__).resolve().parents[3]
GPU = ROOT / "port/gpu/shadps4"
CXX = shutil.which("c++")


@unittest.skipUnless(CXX, "C++ compiler unavailable")
class SpecializationStorageTests(unittest.TestCase):
    def test_current_inputs_and_fetch_storage(self):
        specialization = (GPU / "shader_recompiler/specialization.h").read_text()
        specialization = "\n".join(line for line in specialization.splitlines()
                                   if not line.startswith(("#include", "#pragma")))
        reference = (ROOT / "tools/android/runtime/cpp/specialization_reference.h").read_text()
        fetch = (GPU / "shader_recompiler/frontend/fetch_shader.cpp").read_text()
        fetch_methods = "\n".join(method(fetch, signature) for signature in (
            "static bool IsTypedBufferLoad(",
            "void ParseFetchShader(const Shader::Info& info,",
            "std::optional<FetchShaderData> ParseFetchShader("))
        fixture = (ROOT / "tools/android/runtime/cpp/specialization_storage.cpp").read_text()
        code = fixture.replace("// PRODUCTION_SPECIALIZATION", specialization)
        code = code.replace("// REFERENCE_SPECIALIZATION", reference)
        code = code.replace("// PRODUCTION_FETCH", fetch_methods)
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "test.cpp"
            binary = Path(directory) / "test"
            source.write_text(code)
            built = subprocess.run([CXX, "-std=c++20", "-O2", "-pthread",
                                    "-fsanitize=undefined", "-fno-sanitize-recover=all",
                                    str(source), "-o", str(binary)], text=True, capture_output=True)
            self.assertEqual(built.returncode, 0, built.stderr)
            result = subprocess.run([str(binary)], text=True, capture_output=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("fetch queries=100000 fresh_allocations=100000 retained_allocations=0", result.stdout)
            print(result.stdout.strip())
            # Restoring fresh storage on every cache hit must fail the allocation check.
            old_copy = code.replace("result = it->second.data;",
                                    "result = std::optional<FetchShaderData>{it->second.data};")
            self.assertNotEqual(old_copy, code)
            source.write_text(old_copy)
            built = subprocess.run([CXX, "-std=c++20", "-O2", "-pthread",
                                    str(source), "-o", str(binary)], text=True, capture_output=True)
            self.assertEqual(built.returncode, 0, built.stderr)
            negative = subprocess.run([str(binary)], text=True, capture_output=True, timeout=30)
            self.assertNotEqual(negative.returncode, 0)
            self.assertIn("reused==0", negative.stderr)

    def test_production_selection_owns_scratch_and_no_descriptor_reuse(self):
        cache = (GPU / "video_core/renderer_vulkan/vk_pipeline_cache.cpp").read_text()
        header = (GPU / "video_core/renderer_vulkan/vk_pipeline_cache.h").read_text()
        self.assertIn("std::array<Shader::StageSpecialization, Shader::MaxStageTypes> specialization_scratch", header)
        begin = cache.index("auto& spec = sel.specialization_scratch[")
        end = cache.index("size_t perm_idx", begin)
        self.assertIn("spec.Refresh(info, runtime_info, profile, binding);", cache[begin:end])
        self.assertIn("#else", cache[begin:end])
        self.assertIn("auto spec = Shader::StageSpecialization", cache[begin:end])
        self.assertIn("sel.fetch_shader = std::move(fetch_shader_);", cache)
        pipeline = (GPU / "video_core/renderer_vulkan/vk_pipeline_common.cpp").read_text()
        self.assertNotIn("ReuseGraphicsDescriptors", pipeline)


if __name__ == "__main__":
    unittest.main()
