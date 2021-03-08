/*
 * Copyright (C) 2019 The Android Open Source Project
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
#include <android/log.h>
#include <cstring>
#include <sstream>
#include "mpegh_decoder.h"

extern "C" {
#ifdef __cplusplus
#define __STDC_CONSTANT_MACROS
#ifdef _STDINT_H
#undef _STDINT_H
#endif
#include <stdint.h>
#endif
}

#define LOG_TAG "mpegh_jni"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                                             __VA_ARGS__))
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, \
                                             __VA_ARGS__))
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, \
                                             __VA_ARGS__))

#define DECODER_FUNC(RETURN_TYPE, NAME, ...)                            \
  extern "C" {                                                          \
    JNIEXPORT RETURN_TYPE                                               \
    Java_com_google_android_exoplayer2_ext_mpegh_MpeghDecoder_ ## NAME  \
    (JNIEnv* env, jobject thiz, ##__VA_ARGS__);                         \
  }                                                                     \
  JNIEXPORT RETURN_TYPE                                                 \
  Java_com_google_android_exoplayer2_ext_mpegh_MpeghDecoder_ ## NAME    \
  (JNIEnv* env, jobject thiz, ##__VA_ARGS__)                            \

#define LIBRARY_FUNC(RETURN_TYPE, NAME, ...)                            \
    extern "C" {                                                        \
      JNIEXPORT RETURN_TYPE                                             \
      Java_com_google_android_exoplayer2_ext_mpegh_MpeghLibrary_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);                       \
    }                                                                   \
    JNIEXPORT RETURN_TYPE                                               \
    Java_com_google_android_exoplayer2_ext_mpegh_MpeghLibrary_ ## NAME  \
    (JNIEnv* env, jobject thiz, ##__VA_ARGS__)                          \

using mpegh::MpeghDecoderError;
using mpegh::MpeghDecoder;

enum MpeghJniError : int {
    kInvalidData = -1,
    kErrorOther = -2,
};

enum MpeghCodecType : int {
    kMha1 = 0,
    kMhm1 = 1,
};

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env;
  if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }
  return JNI_VERSION_1_6;
}

LIBRARY_FUNC(jstring, MpeghGetVersion) {
  unsigned int version = sia_mhdr_getVersion();
  unsigned int X = (version >> 16) & 0xFF;
  unsigned int Y = (version >> 8) & 0xFF;
  unsigned int Z = version & 0xFF;
  std::ostringstream stream;
  stream << X << "." << Y << "." << Z;
  return env->NewStringUTF(stream.str().c_str());
}

DECODER_FUNC(jlong, MpeghInitialize, jint codecType, jbyteArray extraData) {

  // mpehg decoder initialize
  mpegh::MpeghDecoder *decoder = new mpegh::MpeghDecoder();
  if (codecType == MpeghCodecType::kMha1) {
    jsize config_size = env->GetArrayLength(extraData);
    jbyte *config = env->GetByteArrayElements(extraData, 0);
    int size = reinterpret_cast<int>(config_size);
    bool ret = decoder->Configure(size, reinterpret_cast<uint8_t *>(config));
    env->ReleaseByteArrayElements(extraData, config, 0);
    if (ret != true) {
      delete decoder;
      decoder = NULL;
    }
  }

  return (jlong) decoder;
}

DECODER_FUNC(jint, MpeghDecode, jlong jHandle, jobject inputData,
             jint inputSize, jobject outputData, jint outputBufferSize) {
  if (!jHandle) {
    LOGE("Handler must be non-NULL.");
    return -1;
  }
  if (!inputData || !outputData) {
    LOGE("Input and output buffers must be non-NULL.");
    return -1;
  }
  if (inputSize < 0) {
    LOGE("Invalid input buffer size: %d.", inputSize);
    return -1;
  }
  if (outputBufferSize < MpeghDecoder::GetOutputSamplePerFrame()
                         * MpeghDecoder::GetOutputChannelCount()
                         * sizeof(float)) {
    LOGE("Invalid output buffer length: %d", outputBufferSize);
    return -1;
  }

  MpeghDecoder *decoder =
      reinterpret_cast<MpeghDecoder *>(jHandle);
  uint8_t *inputBuffer =
      reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(inputData));
  float *outputBuffer =
      reinterpret_cast<float *>(env->GetDirectBufferAddress(outputData));
  int decodedSize = 0;

  MpeghDecoderError ret = MpeghDecoderError::kError;
  ret = decoder->Decode(inputBuffer,
                        inputSize,
                        outputBuffer,
                        &decodedSize);
  if (ret == MpeghDecoderError::kError) {
    return MpeghJniError::kErrorOther;
  } else if (ret == MpeghDecoderError::kInvalidData) {
    return MpeghJniError::kInvalidData;
  }

  return decodedSize;
}

DECODER_FUNC(jint, MpeghGetChannelCount, jlong jHandle) {
  if (!jHandle) {
    LOGE("Handle must be non-NULL.");
    return -1;
  }
  return MpeghDecoder::GetOutputChannelCount();
}

DECODER_FUNC(jint, MpeghGetSampleRate, jlong jHandle) {
  if (!jHandle) {
    LOGE("Handle must be non-NULL.");
    return -1;
  }
  return MpeghDecoder::GetOutputFrequency();
}

DECODER_FUNC(jlong, MpeghReset, jlong jHandle) {
  if (!jHandle) {
    LOGE("Handle must be non-NULL.");
    return 0L;
  }
  MpeghDecoder *decoder =
      reinterpret_cast<MpeghDecoder *>(jHandle);
  decoder->ResetBuffer();
  return (jlong) jHandle;
}

DECODER_FUNC(void, MpeghRelease, jlong jHandle) {
  if (!jHandle) {
    LOGE("Handle must be non-NULL.");
    return;
  }
  MpeghDecoder *decoder =
      reinterpret_cast<MpeghDecoder *>(jHandle);
  decoder->ResetDecoder();
  delete decoder;
  return;
}
