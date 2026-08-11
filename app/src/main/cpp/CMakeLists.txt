cmake_minimum_required(VERSION 3.22.1)
project(mydia_hook CXX)

# 通过 Prefab 找到 ByteHook（build.gradle.kts 已启用 buildFeatures.prefab = true）
find_package(bytehook REQUIRED CONFIG)

add_library(mydia_hook SHARED
        libmydia_hook.cpp)

# 链接 log + bytehook（ByteHook 提供真正的 PLT/GOT hook 能力）
target_link_libraries(mydia_hook log bytehook::bytehook)

set_target_properties(mydia_hook PROPERTIES
        CXX_STANDARD 11
        CXX_STANDARD_REQUIRED ON)
