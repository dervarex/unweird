cmake_minimum_required(VERSION 3.22)
project(unweird C)

# NanoVG (vendored from github.com/memononen/nanovg, see nanovg/COMMIT) with the GLES 3 backend, plus the JNI binding.
add_library(unweird SHARED unweird_vg.c nanovg/nanovg.c)
target_include_directories(unweird PRIVATE nanovg)
target_compile_options(unweird PRIVATE -O2 -Wno-unused-function -Wno-unused-parameter)
target_link_libraries(unweird GLESv3 log m)
