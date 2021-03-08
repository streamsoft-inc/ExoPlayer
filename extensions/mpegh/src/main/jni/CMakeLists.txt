## cmake_version
cmake_minimum_required(VERSION 3.1)

enable_language(CXX)
set(CMAKE_CXX_STANDARD 14)

include_directories(
 ${CMAKE_CURRENT_SOURCE_DIR}
 )

find_library(log-lib
             log)

add_library(mpegh
            SHARED
            mpegh_jni.cc mpegh_decoder.cc)

add_library(SonyIA_mobile STATIC IMPORTED)
set_target_properties(SonyIA_mobile
                      PROPERTIES IMPORTED_LOCATION
                      ${CMAKE_CURRENT_SOURCE_DIR}/mpegh/android-libs/${ANDROID_ABI}/libSonyIA_mobile.a)

add_library(AlcApi STATIC IMPORTED)
set_target_properties(AlcApi
                      PROPERTIES IMPORTED_LOCATION
                      ${CMAKE_CURRENT_SOURCE_DIR}/mpegh/android-libs/${ANDROID_ABI}/libAlcApi.a)

target_link_libraries(
        mpegh SonyIA_mobile AlcApi ${log-lib}
)