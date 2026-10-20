"""Regression for Bionic AHB native submit crashing inside Turnip at addr 0x40."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[3]
VK=ROOT/"port/gpu/shadps4/video_core/renderer_vulkan"
P=(VK/"vk_presenter.cpp").read_text()
AHB=(VK/"vk_swapchain_ahb.h").read_text()
DESKTOP=(VK/"vk_swapchain.h").read_text()
class FirstFrameAhbSubmit(unittest.TestCase):
    def test_never_queue_wait_on_null_semaphore(self):
        self.assertEqual(4,P.count("AddImageAcquiredWait(info, swapchain.GetImageAcquiredSemaphore());"))
        self.assertNotIn("info.AddWait(swapchain.GetImageAcquiredSemaphore())",P)
        self.assertIn("vk_ahb_acquire_wait.h",P)
        self.assertIn("acquire_imported ? image_acquired[image_index]",AHB)
    def test_correct_ahb_layout_and_queue_ownership(self):
        for value in ("acquire_queues.first","acquire_queues.second","release_queues.first","release_queues.second","swapchain.PresentLayout()"):
            self.assertIn(value,P)
        self.assertNotIn(".newLayout = vk::ImageLayout::ePresentSrcKHR",P)
        self.assertIn("vk::ImageLayout::eGeneral;",AHB)
        self.assertIn("VK_QUEUE_FAMILY_FOREIGN_EXT",(VK/"vk_swapchain_ahb.cpp").read_text())
        self.assertIn("vk::ImageLayout::ePresentSrcKHR;",DESKTOP)
if __name__=="__main__":
    unittest.main()
