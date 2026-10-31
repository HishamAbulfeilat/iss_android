"""Regressions: native Bionic graffiti mode must not call ImGui after its init fails.

The Android log showed libtlggpu.so TlgOverlay::ShowGraffitiGuide faulting at 0x8d.
The guest was running at ~30 FPS; do not alter Vulkan, FEX, sensor logic or
the game itself to fix this UI-only crash.
"""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[3]
CPP=(ROOT/"port/gpu/shim/tlg_overlay.cpp").read_text()
APP=(ROOT/"android/app/src/ro/cobrabm/fexdroid/BionicGameActivity.java").read_text()
def section(start,end):
    return CPP.split(start,1)[1].split(end,1)[0]
class BionicGraffitiSafety(unittest.TestCase):
    def test_guide_never_calls_imgui_without_a_context(self):
        body=section("void ShowGraffitiGuide(bool force) {","void BeginSaveList(")
        self.assertIn("if (!initialized)",body)
        self.assertLess(body.index("if (!initialized)"),body.index("OpenGuide();"))
        self.assertIn("WriteAndroidGuideMarker(true);",body)
        self.assertIn("ISS_ANDROID_BIONIC",body)
    def test_android_dialog_preserves_graffiti_controls(self):
        self.assertIn('new File(base,"android-pad.state.guide")',APP)
        self.assertIn("grafite",APP.lower())
        for value in ("L3 + R3","R2","Entendi","analógico esquerdo"):
            self.assertIn(value,APP)
        self.assertIn("graffitiGuideDialog.show()",APP)
        self.assertIn("showGraffitiGuideIfRequested();",APP)
        self.assertIn("graffitiGuideDialog.dismiss()",APP)
    def test_unavailable_save_dialogs_cannot_dereference_imgui_or_accept_delete(self):
        for name,end in [("void BeginSaveList(","int PollSaveList()"),
                         ("void BeginSaveMessage(","int PollSaveMessage()"),
                         ("void CloseSaveDialog()","void SaveProgress(")]:
            body=section(name,end)
            self.assertIn("if (!initialized)",body)
        self.assertIn("message_result = 2",CPP)
if __name__=="__main__":
    unittest.main()
