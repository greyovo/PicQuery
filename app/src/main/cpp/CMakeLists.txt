cmake_minimum_required(VERSION 3.22.1)
project(picquery_xnnpack LANGUAGES CXX)

add_library(picquery_xnnpack SHARED xnnpack_delegate.cpp)
target_compile_features(picquery_xnnpack PRIVATE cxx_std_17)
target_compile_options(picquery_xnnpack PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)
target_include_directories(picquery_xnnpack PRIVATE third_party/litert/include)
target_link_options(picquery_xnnpack PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
target_link_libraries(picquery_xnnpack PRIVATE dl)
