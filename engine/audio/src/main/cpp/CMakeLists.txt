cmake_minimum_required(VERSION 3.22.1)
project(swaralab_oboe)

find_package(oboe REQUIRED CONFIG)

add_library(swaralab_oboe SHARED OboeBridge.cpp)
target_compile_features(swaralab_oboe PRIVATE cxx_std_17)
target_link_libraries(swaralab_oboe oboe::oboe android log)
