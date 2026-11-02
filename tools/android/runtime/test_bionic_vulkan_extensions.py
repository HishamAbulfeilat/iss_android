"""Verify that native Android AHB presentation does not regress to desktop WSI.

The inFAMOUS Bionic launch on Adreno 830 crashed at Vulkan Instance::CreateDevice:
'Required Vulkan extension unavailable: VK_KHR_swapchain'.
Both GoW3 Android and Bloodborne Android require the AHB import + sync-fd
device extensions for their native presenters, not VK_KHR_swapchain.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
VK_INSTANCE = ROOT / "port/gpu/shadps4/video_core/renderer_vulkan/vk_instance.cpp"
AHB_SWAPCHAIN = ROOT / "port/gpu/shadps4/video_core/renderer_vulkan/vk_swapchain_ahb.cpp"


class BionicVulkanExtensionTests(unittest.TestCase):
    def test_android_uses_ahb_extensions_and_desktop_keeps_swapchain(self):
        src = VK_INSTANCE.read_text()
        start = src.index("    // Required", src.index("bool Instance::CreateDevice()"))
        end = src.index("    ASSERT_MSG(add_extension(VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME)", start)
        required = src[start:end]
        self.assertRegex(required, r"#if\s+defined\(__ANDROID__\).*defined\(ISS_ANDROID_BIONIC\)")
        self.assertIn("VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME", required)
        self.assertIn("VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME", required)
        self.assertIn("VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME", required)
        self.assertIn("VK_KHR_SWAPCHAIN_EXTENSION_NAME", required)
        self.assertLess(required.index("#else"), required.index("VK_KHR_SWAPCHAIN_EXTENSION_NAME"))
        self.assertLess(required.index("VK_KHR_SWAPCHAIN_EXTENSION_NAME"), required.index("#endif"))
        self.assertNotIn("VK_KHR_SWAPCHAIN_EXTENSION_NAME", required[:required.index("#else")])

    def test_ahb_backend_uses_external_memory_and_sync_fds(self):
        backend = AHB_SWAPCHAIN.read_text()
        self.assertIn("ExternalMemoryHandleTypeFlagBits::eAndroidHardwareBufferANDROID", backend)
        self.assertIn("ExternalSemaphoreHandleTypeFlagBits::eSyncFd", backend)
        self.assertIn("VK_QUEUE_FAMILY_FOREIGN_EXT", backend)


if __name__ == "__main__":
    unittest.main()
