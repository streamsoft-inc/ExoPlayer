## cmake_version
cmake_minimum_required(VERSION 3.1)

enable_language(CXX)
set(CMAKE_CXX_STANDARD 14)

#[[
# Sets lib_src_DIR to the path of the target CMake project.
set( lib_src_DIR ${CMAKE_CURRENT_SOURCE_DIR}/virtualizer )
# Sets lib_build_DIR to the path of the desired output directory.
set( lib_build_DIR ${CMAKE_CURRENT_SOURCE_DIR}/virtualizer/outputs )
file(MAKE_DIRECTORY ${lib_build_DIR})
# Adds the CMakeLists.txt file located in the specified directory
# as a build dependency.
add_subdirectory( # Specifies the directory of the CMakeLists.txt file.
        ${lib_src_DIR}
        # Specifies the directory for the build outputs.
        ${lib_build_DIR} )
]]

include_directories(
 ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../VirtualizerLib/include/
 )

find_library( # Defines the name of the path variable that stores the
              # location of the NDK library.
              log-lib

              # Specifies the name of the NDK library that
              # CMake needs to locate.
              log )

add_library(virtualizer
            SHARED
            virtualizer_jni.cc )
add_library(SonyVirtualizer STATIC IMPORTED)
set_target_properties(SonyVirtualizer
                      PROPERTIES IMPORTED_LOCATION
                      ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../VirtualizerLib/outputs/android/${ANDROID_ABI}/libSonyVirtualizer.a)

target_link_libraries(
  virtualizer SonyVirtualizer ${log-lib}
)

#link_directories(
#${CMAKE_CURRENT_SOURCE_DIR}/android-libs/${ANDROID_ABI}
#)
