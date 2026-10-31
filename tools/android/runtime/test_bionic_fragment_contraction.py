"""Compile the production policy/profile and floating-point decoration with a SPIR-V sink."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_bionic_resource_reuse import method

ROOT = Path(__file__).resolve().parents[3]
CXX = shutil.which("c++")
GPU = ROOT / "port/gpu/shadps4"

@unittest.skipUnless(CXX, "C++ compiler unavailable")
class FragmentContractionTests(unittest.TestCase):
    def run_policy(self, native):
        emitter = (GPU / "shader_recompiler/backend/spirv/emit_spirv_floating_point.cpp").read_text()
        decorate = method(emitter, "Id Decorate(EmitContext& ctx, IR::Inst* inst, Id op)")
        code = r'''
#include <cassert>
#include <cstdlib>
#include "shader_recompiler/profile.h"
#include "shader_recompiler/backend/spirv/fragment_contraction.h"
namespace spv { enum class Decoration { NoContraction }; }
namespace Shader::Backend::SPIRV {
using Id = unsigned;
namespace IR { struct Inst {}; }
struct EmitContext {
    bool allow_fp_contraction{};
    unsigned decorations{};
    void Decorate(Id op, spv::Decoration decoration) {
        assert(op == 123 && decoration == spv::Decoration::NoContraction);
        ++decorations;
    }
};
''' + decorate + r'''
}
int main() {
    using namespace Shader::Backend::SPIRV;
    unsetenv("TLG_FRAGMENT_CONTRACTION");
    assert(FragmentContractionEnabled() == EXPECT_NATIVE);
    setenv("TLG_FRAGMENT_CONTRACTION", "0", 1);
    assert(!FragmentContractionEnabled());
    setenv("TLG_FRAGMENT_CONTRACTION", "1", 1);
    assert(FragmentContractionEnabled() == EXPECT_NATIVE);
    static_assert(sizeof(Shader::Profile) > 68); // old profile has 68 bytes
    Shader::Profile precise{}, relaxed{};
    relaxed.fragment_contraction_policy = true;
    assert(precise != relaxed); // profile mismatch invalidates cached shaders, including A/B
    for (unsigned enabled = 0; enabled != 2; ++enabled)
    for (unsigned fragment = 0; fragment != 2; ++fragment)
    for (unsigned depth = 0; depth != 2; ++depth)
    for (unsigned targets = 0; targets != 256; ++targets) {
        unsigned count = 0;
        for (unsigned i = 0; i != 8; ++i) count += (targets >> i) & 1;
        bool expected = enabled && fragment && !depth && count <= 1;
        EmitContext ctx{CanContractFragment(enabled, fragment, depth, targets)};
        assert(ctx.allow_fp_contraction == expected);
        assert(Decorate(ctx, nullptr, 123) == 123);
        assert(ctx.decorations == !expected);
    }
}
'''
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "test.cpp"
            binary = Path(directory) / "test"
            source.write_text(code)
            command = [CXX, "-std=c++20", "-O2", "-Wall", "-Wextra",
                       "-Wno-unused-parameter", f"-DEXPECT_NATIVE={int(native)}",
                       "-I", str(GPU), str(source), "-o", str(binary)]
            if native:
                command += ["-D__ANDROID__=1", "-D__BIONIC__=1"]
            subprocess.run(command, check=True, capture_output=True, text=True)
            subprocess.run([str(binary)], check=True, capture_output=True, text=True)

    def test_bionic_policy_and_emission(self):
        self.run_policy(True)

    def test_other_platform_stays_precise(self):
        self.run_policy(False)
