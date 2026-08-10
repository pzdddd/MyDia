cmake_minimum_required(VERSION 3.22.1)
project(mydia_hook CXX)

add_library(mydia_hook SHARED
        libmydia_hook.cpp)

target_link_libraries(mydia_hook log)

# 静态链接 libc（PLT hook 需要替换自身 libc 调用）
set_target_properties(mydia_hook PROPERTIES
        CXX_STANDARD 11
        CXX_STANDARD_REQUIRED ON)
