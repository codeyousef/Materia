cmake_minimum_required(VERSION 3.22)
project(materia_vk_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_library(materia_vk SHARED
        src/main/cpp/vulkan_bridge.cpp
)

target_include_directories(materia_vk
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/include
)

target_compile_definitions(materia_vk
        PRIVATE
        VK_USE_PLATFORM_ANDROID_KHR
)

find_library(log-lib log)
find_library(android-lib android)
find_library(vulkan-lib vulkan)

target_link_libraries(materia_vk
        PRIVATE
        ${log-lib}
        ${android-lib}
        ${vulkan-lib}
)
