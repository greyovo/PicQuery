# The Flutter tooling requires that developers have CMake 3.10 or later installed. You should not increase this version,
# as doing so will cause the plugin to fail to compile for some customers of the plugin.
cmake_minimum_required(VERSION 3.10)

# Project-level configuration.
set(PROJECT_NAME "flutter_onnxruntime")
project(${PROJECT_NAME} LANGUAGES CXX)

# This value is used when generating builds using this plugin, so it must not be changed.
set(PLUGIN_NAME "flutter_onnxruntime_plugin")

# === ONNX Runtime Configuration ===
# Use the DirectML distribution by default.
option(USE_DIRECTML_ONNXRUNTIME "Bundle ONNX Runtime with the DirectML EP" ON)

# Option 1: Use pre-installed ONNX Runtime. A system runtime is retained as an
# explicit opt-out for packagers, but cannot provide DirectML unless they also
# configure the equivalent provider-enabled runtime themselves.
option(USE_SYSTEM_ONNXRUNTIME "Use system-installed ONNX Runtime" OFF)

if(USE_DIRECTML_ONNXRUNTIME)
  set(USE_SYSTEM_ONNXRUNTIME OFF)
endif()

if(USE_SYSTEM_ONNXRUNTIME)
  # Try to find ONNX Runtime manually since pkg-config is less common on Windows

  # Specify custom paths where ONNX Runtime might be installed
  set(ONNXRUNTIME_ROOT_DIR
      ""
      CACHE PATH "ONNX Runtime root directory")

  # Look for the library
  find_library(
    ONNXRUNTIME_LIBRARY
    NAMES onnxruntime
    PATHS ${ONNXRUNTIME_ROOT_DIR}/lib
    DOC "ONNX Runtime library")

  # Look for the include directory
  find_path(
    ONNXRUNTIME_INCLUDE_DIR
    NAMES onnxruntime_cxx_api.h
    PATHS ${ONNXRUNTIME_ROOT_DIR}/include
    DOC "ONNX Runtime include directory")

  if(ONNXRUNTIME_LIBRARY AND ONNXRUNTIME_INCLUDE_DIR)
    set(ONNXRUNTIME_FOUND TRUE)
    set(ONNXRUNTIME_LIBRARIES ${ONNXRUNTIME_LIBRARY})
    set(ONNXRUNTIME_INCLUDE_DIRS ${ONNXRUNTIME_INCLUDE_DIR})
    message(STATUS "Found ONNX Runtime: ${ONNXRUNTIME_LIBRARY}")
  else()
    set(ONNXRUNTIME_FOUND FALSE)
    message(STATUS "System ONNX Runtime not found. Falling back to downloaded version.")
    set(USE_SYSTEM_ONNXRUNTIME OFF)
  endif()
endif()

# Option 2: Download ONNX Runtime if not using system installed version
if(NOT USE_SYSTEM_ONNXRUNTIME)
  include(FetchContent)

  # Set ONNX Runtime version - make sure this is defined Only set if not already defined, to allow the parent project to
  # override it
  if(NOT DEFINED ONNXRUNTIME_VERSION)
    set(ONNXRUNTIME_VERSION
        "1.22.0"
        CACHE STRING "ONNX Runtime version to use")
  endif()

  # Check if version is not empty
  if("${ONNXRUNTIME_VERSION}" STREQUAL "")
    set(ONNXRUNTIME_VERSION "1.22.0")
    message(STATUS "ONNX Runtime version was empty, defaulting to 1.22.0")
  endif()

  # Debug output
  message(STATUS "ONNX Runtime version set to: ${ONNXRUNTIME_VERSION}")

  # Determine architecture for download URL
  if(CMAKE_SIZEOF_VOID_P EQUAL 8)
    set(ONNXRUNTIME_ARCH "x64")
  else()
    set(ONNXRUNTIME_ARCH "x86")
  endif()

  if(USE_DIRECTML_ONNXRUNTIME)
    # NuGet's DirectML package supplies the matching import library, provider
    # DLLs, and DirectML redistributable in one archive.
    set(ONNXRUNTIME_URL
        "https://www.nuget.org/api/v2/package/Microsoft.ML.OnnxRuntime.DirectML/${ONNXRUNTIME_VERSION}"
    )
  else()
    set(ONNXRUNTIME_URL
        "https://github.com/microsoft/onnxruntime/releases/download/v${ONNXRUNTIME_VERSION}/onnxruntime-win-${ONNXRUNTIME_ARCH}-${ONNXRUNTIME_VERSION}.zip"
    )
  endif()

  message(STATUS "Downloading ONNX Runtime from: ${ONNXRUNTIME_URL}")

  # Create a directory for the downloaded library
  set(ONNXRUNTIME_DOWNLOAD_DIR "${CMAKE_CURRENT_BINARY_DIR}/onnxruntime")

  # Download and extract ONNX Runtime - using file(DOWNLOAD) for more direct control
  if(USE_DIRECTML_ONNXRUNTIME)
    set(ONNXRUNTIME_EXTRACT_DIR "${ONNXRUNTIME_DOWNLOAD_DIR}/directml")
    set(ONNXRUNTIME_HEADER "${ONNXRUNTIME_EXTRACT_DIR}/build/native/include/onnxruntime_cxx_api.h")
  else()
    set(ONNXRUNTIME_EXTRACT_DIR "${ONNXRUNTIME_DOWNLOAD_DIR}/onnxruntime-win-${ONNXRUNTIME_ARCH}-${ONNXRUNTIME_VERSION}")
    set(ONNXRUNTIME_HEADER "${ONNXRUNTIME_EXTRACT_DIR}/include/onnxruntime_cxx_api.h")
  endif()

  if(NOT EXISTS "${ONNXRUNTIME_HEADER}")
    message(STATUS "Downloading ONNX Runtime...")
    file(MAKE_DIRECTORY ${ONNXRUNTIME_DOWNLOAD_DIR})

    # Download the zipfile
    set(ONNXRUNTIME_ZIPFILE "${ONNXRUNTIME_DOWNLOAD_DIR}/onnxruntime.zip")
    file(DOWNLOAD ${ONNXRUNTIME_URL} ${ONNXRUNTIME_ZIPFILE} SHOW_PROGRESS)

    # Extract the zipfile
    message(STATUS "Extracting ONNX Runtime...")
    file(MAKE_DIRECTORY ${ONNXRUNTIME_EXTRACT_DIR})
    execute_process(COMMAND ${CMAKE_COMMAND} -E tar xf ${ONNXRUNTIME_ZIPFILE}
                    WORKING_DIRECTORY ${ONNXRUNTIME_EXTRACT_DIR})

    # Clean up the zipfile
    file(REMOVE ${ONNXRUNTIME_ZIPFILE})
  endif()

  # Verify the directory exists
  if(NOT EXISTS ${ONNXRUNTIME_EXTRACT_DIR})
    message(FATAL_ERROR "ONNX Runtime extraction directory doesn't exist: ${ONNXRUNTIME_EXTRACT_DIR}")
  endif()

  # Set include and library directories
  if(USE_DIRECTML_ONNXRUNTIME)
    set(ONNXRUNTIME_INCLUDE_DIRS "${ONNXRUNTIME_EXTRACT_DIR}/build/native/include")
    set(ONNXRUNTIME_NATIVE_DIR "${ONNXRUNTIME_EXTRACT_DIR}/runtimes/win-${ONNXRUNTIME_ARCH}/native")
  else()
    set(ONNXRUNTIME_INCLUDE_DIRS "${ONNXRUNTIME_EXTRACT_DIR}/include")
    set(ONNXRUNTIME_NATIVE_DIR "${ONNXRUNTIME_EXTRACT_DIR}/lib")
  endif()

  # Log directory contents to help with debugging
  message(STATUS "ONNX Runtime extract directory: ${ONNXRUNTIME_EXTRACT_DIR}")
  file(GLOB_RECURSE ONNXRUNTIME_FILES "${ONNXRUNTIME_EXTRACT_DIR}/*")
  foreach(FILE ${ONNXRUNTIME_FILES})
    message(STATUS "Found file: ${FILE}")
  endforeach()

  # Find the library files (.dll and .lib)
  if(EXISTS "${ONNXRUNTIME_NATIVE_DIR}/onnxruntime.lib")
    set(ONNXRUNTIME_LIBRARY "${ONNXRUNTIME_NATIVE_DIR}/onnxruntime.lib")
    set(ONNXRUNTIME_DLL "${ONNXRUNTIME_NATIVE_DIR}/onnxruntime.dll")
  else()
    # Try to find the library in case the structure changed
    file(GLOB_RECURSE ONNXRUNTIME_LIB_CANDIDATES "${ONNXRUNTIME_EXTRACT_DIR}/*onnxruntime*.lib")

    if(ONNXRUNTIME_LIB_CANDIDATES)
      list(GET ONNXRUNTIME_LIB_CANDIDATES 0 ONNXRUNTIME_LIBRARY)
      # Find the corresponding DLL
      get_filename_component(LIB_PATH ${ONNXRUNTIME_LIBRARY} DIRECTORY)
      file(GLOB ONNXRUNTIME_DLL_CANDIDATES "${LIB_PATH}/*onnxruntime*.dll")
      if(ONNXRUNTIME_DLL_CANDIDATES)
        list(GET ONNXRUNTIME_DLL_CANDIDATES 0 ONNXRUNTIME_DLL)
      endif()
      message(STATUS "Found ONNX Runtime library: ${ONNXRUNTIME_LIBRARY}")
      message(STATUS "Found ONNX Runtime DLL: ${ONNXRUNTIME_DLL}")
    else()
      message(FATAL_ERROR "Could not find ONNX Runtime library in extracted files")
    endif()
  endif()

  set(ONNXRUNTIME_LIBRARIES ${ONNXRUNTIME_LIBRARY})

  # DirectML requires companion provider and redistributable DLLs alongside
  # onnxruntime.dll. Bundle every native DLL from the selected package.
  file(GLOB ONNXRUNTIME_DLLS "${ONNXRUNTIME_NATIVE_DIR}/*.dll")
  set(flutter_onnxruntime_bundled_libraries ${ONNXRUNTIME_DLLS})
endif()

# Define Windows-specific preprocessor macros
add_definitions(-DWIN32_LEAN_AND_MEAN -DNOMINMAX -DUNICODE -D_UNICODE)

# Any new source files that you add to the plugin should be added here.
list(APPEND PLUGIN_SOURCES "src/session_manager.cc" "src/value_conversion.cc" "src/tensor_manager.cc"
     "src/windows_utils.cc")

# Define the plugin library target. Its name must not be changed (see comment on PLUGIN_NAME above).
add_library(${PLUGIN_NAME} SHARED "flutter_onnxruntime_plugin.cpp" "flutter_onnxruntime_plugin.h" ${PLUGIN_SOURCES})

# Apply a standard set of build settings that are configured in the application-level CMakeLists.txt. This can be
# removed for plugins that want full control over build settings.
apply_standard_settings(${PLUGIN_NAME})

# Symbols are hidden by default to reduce the chance of accidental conflicts between plugins. This should not be
# removed; any symbols that should be exported should be explicitly exported with the FLUTTER_PLUGIN_EXPORT macro.
set_target_properties(${PLUGIN_NAME} PROPERTIES CXX_VISIBILITY_PRESET hidden)
target_compile_definitions(${PLUGIN_NAME} PRIVATE FLUTTER_PLUGIN_IMPL)
if(USE_DIRECTML_ONNXRUNTIME)
  target_compile_definitions(${PLUGIN_NAME} PRIVATE FLUTTER_ONNXRUNTIME_DIRECTML=1)
endif()

# Add WINDOWS_EXPORT_ALL_SYMBOLS to ensure the generation of the import library (.lib file)
set_target_properties(${PLUGIN_NAME} PROPERTIES WINDOWS_EXPORT_ALL_SYMBOLS ON)

# Enable C++17 features
target_compile_features(${PLUGIN_NAME} PRIVATE cxx_std_17)

# The plugin sources are UTF-8. MSVC otherwise uses the active ANSI code page
# (for example CP936), which turns Unicode comments into C4819 warnings. The
# host application's standard settings promote warnings to errors.
if(MSVC)
  target_compile_options(${PLUGIN_NAME} PRIVATE /utf-8)
endif()

# Source include directories and library dependencies. Add any plugin-specific dependencies here.
target_include_directories(
  ${PLUGIN_NAME} PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include" "${CMAKE_CURRENT_SOURCE_DIR}" "${FLUTTER_EPHEMERAL_DIR}"
                         "${CMAKE_CURRENT_SOURCE_DIR}/windows/include" "${ONNXRUNTIME_INCLUDE_DIRS}")

# Install debugging message
message(STATUS "Include directories: ${CMAKE_CURRENT_SOURCE_DIR}/include")
message(STATUS "Binary directory: ${CMAKE_CURRENT_BINARY_DIR}/include")
message(STATUS "This file: ${CMAKE_CURRENT_LIST_FILE}")

# Install public headers for the plugin
install(FILES "include/flutter_onnxruntime/flutter_onnxruntime_plugin.h" "include/flutter_onnxruntime/export.h"
        DESTINATION "${CMAKE_BINARY_DIR}/include/flutter_onnxruntime")

# Also create symlinks in the build directory to ensure includes work during build
file(MAKE_DIRECTORY "${CMAKE_BINARY_DIR}/include/flutter_onnxruntime")

# Copy headers with error handling
file(
  COPY "${CMAKE_CURRENT_SOURCE_DIR}/include/flutter_onnxruntime/flutter_onnxruntime_plugin.h"
  DESTINATION "${CMAKE_BINARY_DIR}/include/flutter_onnxruntime/"
  FOLLOW_SYMLINK_CHAIN)

file(
  COPY "${CMAKE_CURRENT_SOURCE_DIR}/include/flutter_onnxruntime/export.h"
  DESTINATION "${CMAKE_BINARY_DIR}/include/flutter_onnxruntime/"
  FOLLOW_SYMLINK_CHAIN)

# Add include directories for the plugin
target_include_directories(${PLUGIN_NAME} PUBLIC $<BUILD_INTERFACE:${CMAKE_BINARY_DIR}/include>
                                                 $<INSTALL_INTERFACE:include>)

# Add ONNX Runtime include directories
target_include_directories(${PLUGIN_NAME} PRIVATE ${ONNXRUNTIME_INCLUDE_DIRS})

# Link against Flutter and other libraries
target_link_libraries(${PLUGIN_NAME} PRIVATE flutter flutter_wrapper_plugin)

# Link against ONNX Runtime
target_link_libraries(${PLUGIN_NAME} PRIVATE ${ONNXRUNTIME_LIBRARIES})

# Link against Windows Shell API library for PathRemoveFileSpecW
target_link_libraries(${PLUGIN_NAME} PRIVATE Shlwapi)

# Copy over ONNX Runtime DLL to the build directory
if(NOT USE_SYSTEM_ONNXRUNTIME AND ONNXRUNTIME_DLLS)
  foreach(ONNXRUNTIME_RUNTIME_DLL IN LISTS ONNXRUNTIME_DLLS)
    add_custom_command(
      TARGET ${PLUGIN_NAME}
      POST_BUILD
      COMMAND ${CMAKE_COMMAND} -E copy_if_different ${ONNXRUNTIME_RUNTIME_DLL} $<TARGET_FILE_DIR:${PLUGIN_NAME}>
      COMMENT "Copying ONNX Runtime DLL to build directory")
  endforeach()
endif()

# List of absolute paths to libraries that should be bundled with the plugin. This list could contain prebuilt
# libraries, or libraries created by an external build triggered from this build file.
set(flutter_onnxruntime_bundled_libraries
    "${flutter_onnxruntime_bundled_libraries}"
    PARENT_SCOPE)
