# Included by FEX's project() (CMAKE_PROJECT_FEX_INCLUDE, see build.sh): FEX's CMake files expect to
# be the top-level project, so libisscpu is added to FEX's build once all of its targets exist.
# EVAL expands the path now; deferred arguments are expanded when the call runs.
cmake_language(EVAL CODE "cmake_language(DEFER DIRECTORY \"${CMAKE_SOURCE_DIR}\" CALL include \"${CMAKE_CURRENT_LIST_DIR}/isscpu.cmake\")")

# FEX's top-level CMake rejects Android as an OS identifier, although the
# embedded FEX CPU targets use Linux kernel source APIs when built with the NDK.
# GoW3 and Bloodborne use the same trick: retain NDK toolchain and Bionic ABI,
# but present Linux to the FEX-only operating system test.
if(ANDROID AND CMAKE_SYSTEM_NAME STREQUAL "Android")
  set(CMAKE_SYSTEM_NAME Linux)
endif()
