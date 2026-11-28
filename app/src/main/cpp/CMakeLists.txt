cmake_minimum_required(VERSION 3.22.1)

project("ciphervirtual")

# Include header directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# Compile shared native engine library
add_library(
    ciphervirtual
    SHARED
    ciphervirtual_jni.cpp
    syscall_interceptor.cpp
    virtual_sandbox.cpp
)

# Link against Android NDK system libraries
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    ciphervirtual
    ${log-lib}
    ${android-lib}
)
