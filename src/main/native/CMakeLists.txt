cmake_minimum_required(VERSION 3.20)
project(viergewinnt_dx12 LANGUAGES CXX)

find_package(Java REQUIRED COMPONENTS Development)

get_filename_component(JAVA_HOME "${Java_JAVA_EXECUTABLE}" DIRECTORY)
get_filename_component(JAVA_HOME "${JAVA_HOME}" DIRECTORY)

add_library(viergewinnt_dx12 SHARED dx12_bridge.cpp)
target_include_directories(viergewinnt_dx12 PRIVATE
    "${JAVA_HOME}/include"
    "${JAVA_HOME}/include/win32")
target_compile_features(viergewinnt_dx12 PRIVATE cxx_std_17)
target_link_libraries(viergewinnt_dx12 PRIVATE d3d12 dxgi d3dcompiler)
if(MINGW)
    target_link_options(viergewinnt_dx12 PRIVATE -static -static-libgcc -static-libstdc++)
endif()
set_target_properties(viergewinnt_dx12 PROPERTIES PREFIX "" OUTPUT_NAME viergewinnt_dx12)
