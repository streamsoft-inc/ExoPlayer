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
#include "alc.h"

extern "C" {
#ifdef __cplusplus
#define __STDC_CONSTANT_MACROS
#ifdef _STDINT_H
#undef _STDINT_H
#endif
#include <stdint.h>
#endif
}

#define LOG_TAG "Alc_jni"
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
    Java_com_google_android_exoplayer2_ext_mpegh_AlcAudioProcessor_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_mpegh_AlcAudioProcessor_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\

static constexpr int NUMBER_OF_CHANNELS = 2;

LIBRARY_FUNC(jlong, AlcInitialize) {
  // LOGV("AlcInitialize() called");
  alc::Alc* alcInstance = new alc::Alc();
  return (jlong)alcInstance;
}

LIBRARY_FUNC(jboolean, AlcProcFrame, jlong jHandle,
             jint offset, jobject ioData, jlong inputSize) {
  // LOGV("AlcProcFrame() called inputSampleSize:%d", inputSampleSize);
  if (!jHandle) {
    LOGE("Handler must be non-NULL.");
    return false;
  }
  if (!ioData) {
    LOGE("Input and output buffers must be non-NULL.");
    return false;
  }

  alc::Alc* alc = reinterpret_cast<alc::Alc*>(jHandle);
  float *ioBuffer = reinterpret_cast<float*>(env->GetDirectBufferAddress(ioData));
  int wroteDataSize = inputSize;

  alc::AlcError ret = alc->ProcFrame(offset, ioBuffer, &wroteDataSize);
  if (ret != alc::AlcError::kNoError) {
      return false;
  }

  return true;;
}

LIBRARY_FUNC(void, AlcRelease, jlong jHandle) {
  // LOGV("AlcRelease() called");
  if (!jHandle) {
    LOGE("Invalid param : jHandle");
    return;
  }
  alc::Alc* handle =
    reinterpret_cast<alc::Alc*>(jHandle);
  delete handle;
}
