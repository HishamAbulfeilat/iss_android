# libisscpu.so: guest_cpu.h on FEXCore, linked statically against FEX's libraries.
set(ISSCPU_DIR "${CMAKE_CURRENT_LIST_DIR}")
add_library(isscpu SHARED "${ISSCPU_DIR}/guest_cpu_fex.cpp" "${ISSCPU_DIR}/host_call_arm64.S")
target_include_directories(isscpu PRIVATE "${ISSCPU_DIR}/../port/src" "${CMAKE_SOURCE_DIR}/Source" "${CMAKE_BINARY_DIR}/generated")
target_link_libraries(isscpu PRIVATE FEXCore Common JemallocLibs fmt::fmt)
target_link_options(isscpu PRIVATE -Wl,--no-undefined)
target_compile_options(isscpu PRIVATE ${FEX_TUNE_COMPILE_FLAGS})
set_target_properties(isscpu PROPERTIES
  LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}"
  C_VISIBILITY_PRESET hidden
  CXX_VISIBILITY_PRESET hidden
  VISIBILITY_INLINES_HIDDEN TRUE)
