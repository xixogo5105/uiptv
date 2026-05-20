cmake_minimum_required(VERSION 3.22.1)

project(uiptv_vlc_probe)

add_library(uiptv_vlc_probe SHARED vlc_probe.cpp)

target_compile_features(uiptv_vlc_probe PRIVATE cxx_std_17)

find_library(log-lib log)

target_link_libraries(uiptv_vlc_probe PRIVATE ${log-lib} dl)
