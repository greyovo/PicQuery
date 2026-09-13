// Diagnostic JNI shim only. Compile with the pinned official LiteRT v1.4.1
// headers supplied by the host; never substitute a hand-written options layout.
#include <jni.h>
#include <dlfcn.h>

#include <cstdint>
#include <memory>
#include <mutex>
#include <new>
#include <stdexcept>
#include <string>
#include <unordered_set>

#include "tflite/delegates/xnnpack/xnnpack_delegate.h"

namespace {

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
    // TensorFlowLite.init() runs before System.load() of this probe.
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
  // pointers cannot outlive their library. This probe has no unload operation.
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
    ThrowJava(env, "java/lang/OutOfMemoryError", "Cannot allocate native XNNPACK probe state");
  } catch (const std::exception& error) {
    ThrowJava(env, "java/lang/IllegalStateException", error.what());
  } catch (...) {
    ThrowJava(env, "java/lang/IllegalStateException", "Unknown native XNNPACK probe failure");
  }
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_me_grey_picquery_feature_MobileCLIPVersionsBenchmarkTest_00024NativeXnnpackDelegate_nativeCreate(
    JNIEnv* env, jobject, jint cpu_threads) {
  if (cpu_threads < 1 || cpu_threads > 8) {
    ThrowJava(env, "java/lang/IllegalArgumentException", "Native XNNPACK thread count must be 1..8");
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
Java_me_grey_picquery_feature_MobileCLIPVersionsBenchmarkTest_00024NativeXnnpackDelegate_nativeHasThreadPool(
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
Java_me_grey_picquery_feature_MobileCLIPVersionsBenchmarkTest_00024NativeXnnpackDelegate_nativeDelete(
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
