/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <cstring>
#include <android/log.h>
#include "libSonyVirtualizer.h"

extern "C" {
#ifdef __cplusplus
#define __STDC_CONSTANT_MACROS
#ifdef _STDINT_H
#undef _STDINT_H
#endif
#include <stdint.h>
#endif
}

#define LOG_TAG "Virtualizer_jni"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                   __VA_ARGS__))
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, \
                   __VA_ARGS__))
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, \
                   __VA_ARGS__))
#define LOGV(...) ((void)__android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, \
                   __VA_ARGS__))

#define LIBRARY_FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_virtualizer_VirtualizerAudioProcessor_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_virtualizer_VirtualizerAudioProcessor_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

static constexpr int NUMBER_OF_CHANNELS = 2;

/**
 * Allocates and opens a new handler for the virtualizer audio processor, passing the
 * provided extraData as initialization data for the decoder
 * Returns the created context.
 */
SIA_3DV_HANDLE createHandle(JNIEnv *env ,jint fs, jstring appRootPath,
                            jstring configFilePathHrtf, jstring configFilePathCp);
/**
 * Releases the specified handle.
 */
void releaseHandle(SIA_3DV_HANDLE handle);

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }
  return JNI_VERSION_1_6;
}

LIBRARY_FUNC(jstring, VirtualizerGetVersion) {
  // LOGV("VirtualizerGetVersion() called");
  SiaVersion version = sia_3dv_get_version();
  char versionStr[16];
  snprintf(versionStr, sizeof(versionStr), "V%d.%d.%d",
           version.major, version.minorA, version.minorB);
  return env->NewStringUTF(&versionStr[0]);
}

LIBRARY_FUNC(jlong, VirtualizerInitialize, jint fs,
             jstring rootPath, jstring configFilePathHrtf,
             jstring configFilePathCp) {
  // LOGV("VirtualizerInitialize() called");
  return (jlong) createHandle(env, fs,
                              rootPath, configFilePathHrtf, configFilePathCp);
}

LIBRARY_FUNC(jboolean, VirtualizerQueueInput, jlong jHandle,
             jobject inputData, jint inputSampleSize) {
  // LOGV("VirtualizerQueueInput() called inputSampleSize:%d", inputSampleSize);
  if (!jHandle) {
    LOGE("Handler must be non-NULL.");
    return false;
  }
  if (!inputData) {
    LOGE("Input and output buffers must be non-NULL.");
    return false;
  }
  if (inputSampleSize < 0) {
    LOGE("Invalid input buffer size: %d.", inputSampleSize);
    return false;
  }

  SIA_3DV_HANDLE handle = reinterpret_cast<SIA_3DV_HANDLE>(jHandle);
  float *inputBuffer = reinterpret_cast<float*>(env->GetDirectBufferAddress(inputData));

  SiaStatus ret = sia_3dv_queue_input(handle, inputSampleSize, inputBuffer, false);
  if (ret != kSiaSuccess) {
      LOGE("sia_3dv_queue_input error:%d", ret);
      return false;
  }

  return true;
}

LIBRARY_FUNC(jint, VirtualizerGetOutput, jlong jHandle,
             jobject outputData, jint outputCapacity) {
  // LOGD("VirtualizerGetOutput() called");
  if (!jHandle) {
    LOGE("Handler must be non-NULL.");
    return -1;
  }
  if (!outputData) {
    LOGE("Input and output buffers must be non-NULL.");
    return -1;
  }
  if (outputCapacity < 0) {
    LOGE("Invalid output buffer size: %d.", outputCapacity);
    return -1;
  }

  SIA_3DV_HANDLE handle =
      reinterpret_cast<SIA_3DV_HANDLE>(jHandle);
  float *outputBuffer = reinterpret_cast<float*>(env->GetDirectBufferAddress(outputData));

  unsigned int outputSize;
  SiaStatus ret = sia_3dv_get_output(handle, outputCapacity, outputBuffer, &outputSize);
  if (ret != kSiaSuccess && ret != kSiaEeos && ret != kSiaEagain) {
      LOGE("sia_3dv_get_output error:%d", ret);
      return -1;
  }

  return outputSize;
}

LIBRARY_FUNC(jint, VirtualizerGetChannelCount, jlong jHandle) {
  // LOGV("VirtualizerGetChannelCount() called");
  if (!jHandle) {
    LOGE("Handle must be non-NULL.");
    return -1;
  }
  return NUMBER_OF_CHANNELS;
}

LIBRARY_FUNC(jint, VirtualizerGetSampleRate, jlong jHandle) {
  // LOGV("VirtualizerGetSampleRate() called");
  if (!jHandle) {
    LOGE("Handle must be non-NULL.");
    return -1;
  }
  SIA_3DV_HANDLE handle =
      reinterpret_cast<SIA_3DV_HANDLE>(jHandle);
  SiaFs sia_fs;
  int fs = 0;
  SiaStatus ret = sia_3dv_get_output_fs(handle, &sia_fs);
  if (ret != kSiaSuccess) {
      LOGE("sia_3dv_get_output_fs error:%d", ret);
      return -1;
  }
  switch (sia_fs) {
    case kSiaFs44100:
      fs = 44100;
      break;
    case kSiaFs48000:
      fs = 48000;
      break;
    default:
      return -1;
  }
  return fs;
}

LIBRARY_FUNC(jlong, VirtualizerReset, jlong jHandle) {
  // LOGV("VirtualizerReset() called");
  if (!jHandle) {
    LOGE("Invalid param : jHandle");
    return 0L;
  }
  SIA_3DV_HANDLE handle =
    reinterpret_cast<SIA_3DV_HANDLE>(jHandle);

  sia_3dv_reset(handle);
  return (jlong) jHandle;
}

SIA_3DV_HANDLE createHandle(JNIEnv *env , jint input_fs, jstring appRootPath,
                            jstring configFilePathHrtf, jstring configFilePathCp) {

  SIA_3DV_HANDLE handle;
  unsigned int buffSize = 61440;

  SiaStatus ret = sia_3dv_get_handle(&handle, buffSize);
  if (ret != kSiaSuccess) {
    LOGE("sia_3dv_get_handle error:%d", ret);
    return 0L;
  }
  // LOGV("sia_3da_getHandle() handle:%p", handle);

  const char* root = env->GetStringUTFChars(appRootPath, 0);
  const char* hrtf = env->GetStringUTFChars(configFilePathHrtf, 0);
  const char* cp = env->GetStringUTFChars(configFilePathCp, 0);

  SiaFs output_sia_fs;
  SiaFs input_sia_fs;
  bool through_mode = false;
  switch (input_fs) {
      case 32000:
          input_sia_fs = kSiaFs32000;
          break;
      case 44100:
          input_sia_fs = kSiaFs44100;
          break;
      case 48000:
          input_sia_fs = kSiaFs48000;
          break;
      case 96000:
          input_sia_fs = kSiaFs96000;
          break;
      case 192000:
          input_sia_fs = kSiaFs192000;
          break;
      default:
          return 0L;
  }
  ret = sia_3dv_init(handle, root, hrtf, cp, input_sia_fs, &output_sia_fs, through_mode);
  if (ret != kSiaSuccess) {
    LOGE("sia_3dv_init error:%d", ret);
    sia_3dv_free_handle(handle);
    return 0L;
  }
  return handle;
}

void releaseHandle(SIA_3DV_HANDLE handle) {
  if (handle) {
    sia_3dv_free_handle(handle);
    handle = NULL;
  }
}

LIBRARY_FUNC(void, VirtualizerRelease, jlong jHandle) {
  // LOGD("VirtualizerRelease() called");
  if (!jHandle) {
    LOGE("Invalid param : jHandle");
    return;
  }
  SIA_3DV_HANDLE handle =
      reinterpret_cast<SIA_3DV_HANDLE>(jHandle);
  releaseHandle(handle);
  return;
}
