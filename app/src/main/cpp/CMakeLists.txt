cmake_minimum_required(VERSION 3.10)

project("obd2-qcm6125-native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Include directories
include_directories(.)

# Source files
set(SOURCES
    NativeHelper.cpp
    NativeHelper_Mock.cpp
)

# Generate the library
add_library(native-helper-cmock SHARED ${SOURCES})

# Link with standard C++
target_link_libraries(native-helper-cmock stdc++)
