// XNNPACK bridge using official headers verified against LiteRT 1.4.2.
// See third_party/litert/README.md for source and binary ABI evidence.
#include <jni.h>
#include <dlfcn.h>

#include <cstdint>
#include <cstddef>
#include <memory>
#include <mutex>
#include <new>
#include <stdexcept>
#include <string>
#include <unordered_set>

#include "tflite/delegates/xnnpack/xnnpack_delegate.h"

namespace {

// OptionsDefault returns this structure by value. A stale, smaller header lets
// the library overwrite the caller's stack before any delegate is created.
static_assert(sizeof(void*) == 4 || sizeof(void*) == 8);
static_assert(sizeof(TfLiteXNNPackDelegateOptions) == (sizeof(void*) == 8 ? 64 : 36));
static_assert(alignof(TfLiteXNNPackDelegateOptions) == sizeof(void*));
static_assert(offsetof(TfLiteXNNPackDelegateOptions, num_threads) == 0);
static_assert(offsetof(TfLiteXNNPackDelegateOptions, runtime_flags) == 4);
static_assert(offsetof(TfLiteXNNPackDelegateOptions, flags) == 8);
static_assert(offsetof(TfLiteXNNPackDelegateOptions, weights_cache) == (sizeof(void*) == 8 ? 16 : 12));
static_assert(offsetof(TfLiteXNNPackDelegateOptions, handle_variable_ops) == (sizeof(void*) == 8 ? 24 : 16));
static_assert(offsetof(TfLiteXNNPackDelegateOptions, weight_cache_file_path) == (sizeof(void*) == 8 ? 32 : 20));
static_assert(offsetof(TfLiteXNNPackDelegateOptions, weight_cache_file_descriptor) == (sizeof(void*) == 8 ? 40 : 24));
static_assert(offsetof(TfLiteXNNPackDelegateOptions, weight_cache_provider) == (sizeof(void*) == 8 ? 48 : 28));
static_assert(offsetof(TfLiteXNNPackDelegateOptions, weight_cache_lock_memory) == (sizeof(void*) == 8 ? 56 : 32));

void ThrowJava(JNIEnv* env, const char* type, const char* message) {
  if (env->ExceptionCheck()) return;
  jclass exception_class = env->FindClass(type);
  if (exception_class != nullptr) {
    env->ThrowNew(exception_class, message);
    env->DeleteLocalRef(exception_class);
  }
}

template <typename Function>
Function Resolve(void* library, const char* name) {
  dlerror();
  void* symbol = dlsym(library, name);
  const char* error = dlerror();
  if (error != nullptr || symbol == nullptr) {
    throw std::runtime_error(std::string("Missing LiteRT symbol ") + name +
                             ": " + (error == nullptr ? "null address" : error));
  }
  return reinterpret_cast<Function>(symbol);
}

struct Api {
  void* library = nullptr;
  decltype(&TfLiteXNNPackDelegateOptionsDefault) defaults = nullptr;
  decltype(&TfLiteXNNPackDelegateCreate) create = nullptr;
  decltype(&TfLiteXNNPackDelegateDelete) destroy = nullptr;
  decltype(&TfLiteXNNPackDelegateGetThreadPool) thread_pool = nullptr;

  Api() {
    // TensorFlowLite.init() runs before System.loadLibrary() of this bridge.
    library = dlopen("libtensorflowlite_jni.so", RTLD_NOW | RTLD_LOCAL);
    if (library == nullptr) {
      const char* error = dlerror();
      throw std::runtime_error(error == nullptr ? "Cannot open existing LiteRT JNI library" : error);
    }
    try {
      defaults = Resolve<decltype(defaults)>(library, "TfLiteXNNPackDelegateOptionsDefault");
      create = Resolve<decltype(create)>(library, "TfLiteXNNPackDelegateCreate");
      destroy = Resolve<decltype(destroy)>(library, "TfLiteXNNPackDelegateDelete");
      thread_pool = Resolve<decltype(thread_pool)>(library, "TfLiteXNNPackDelegateGetThreadPool");
    } catch (...) {
      dlclose(library);
      library = nullptr;
      throw;
    }
  }
  // Retain the dlopen reference for the process lifetime so delegate function
  // pointers cannot outlive their library. This bridge has no unload operation.
};

Api& GetApi() {
  static Api api;
  return api;
}

std::mutex delegates_mutex;
std::unordered_set<TfLiteDelegate*> live_delegates;

TfLiteDelegate* CheckedDelegate(jlong handle) {
  auto* delegate = reinterpret_cast<TfLiteDelegate*>(static_cast<intptr_t>(handle));
  if (delegate == nullptr || live_delegates.find(delegate) == live_delegates.end()) {
    throw std::runtime_error("Unknown or already closed XNNPACK delegate handle");
  }
  return delegate;
}

void TranslateException(JNIEnv* env) {
  try {
    throw;
  } catch (const std::bad_alloc&) {
    ThrowJava(env, "java/lang/OutOfMemoryError", "Cannot allocate native XNNPACK delegate state");
  } catch (const std::exception& error) {
    ThrowJava(env, "java/lang/IllegalStateException", error.what());
  } catch (...) {
    ThrowJava(env, "java/lang/IllegalStateException", "Unknown native XNNPACK delegate failure");
  }
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_me_grey_picquery_feature_tf_NativeXnnpackDelegate_nativeCreate(
    JNIEnv* env, jobject, jint cpu_threads) {
  if (cpu_threads < 1) {
    ThrowJava(env, "java/lang/IllegalArgumentException", "Native XNNPACK thread count must be positive");
    return 0;
  }
  try {
    Api& api = GetApi();
    TfLiteXNNPackDelegateOptions options = api.defaults();
    options.num_threads = cpu_threads;  // Preserve every other official default.
    std::unique_ptr<TfLiteDelegate, decltype(api.destroy)> delegate(api.create(&options), api.destroy);
    if (delegate == nullptr) throw std::runtime_error("TfLiteXNNPackDelegateCreate returned null");
    std::lock_guard<std::mutex> lock(delegates_mutex);
    live_delegates.insert(delegate.get());
    return static_cast<jlong>(reinterpret_cast<intptr_t>(delegate.release()));
  } catch (...) {
    TranslateException(env);
    return 0;
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_me_grey_picquery_feature_tf_NativeXnnpackDelegate_nativeHasThreadPool(
    JNIEnv* env, jobject, jlong handle) {
  try {
    std::lock_guard<std::mutex> lock(delegates_mutex);
    return GetApi().thread_pool(CheckedDelegate(handle)) != nullptr ? JNI_TRUE : JNI_FALSE;
  } catch (...) {
    TranslateException(env);
    return JNI_FALSE;
  }
}

extern "C" JNIEXPORT void JNICALL
Java_me_grey_picquery_feature_tf_NativeXnnpackDelegate_nativeDelete(
    JNIEnv* env, jobject, jlong handle) {
  try {
    std::lock_guard<std::mutex> lock(delegates_mutex);
    TfLiteDelegate* delegate = CheckedDelegate(handle);
    live_delegates.erase(delegate);
    GetApi().destroy(delegate);
  } catch (...) {
    TranslateException(env);
  }
}
