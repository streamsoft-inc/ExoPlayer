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
#include "SonyIA_mobile_Virtualizer.h"

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
    Java_com_google_android_exoplayer2_ext_mpegh_VirtualizerAudioProcessor_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_mpegh_VirtualizerAudioProcessor_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\


/**
 * Allocates and opens a new handler for the virtualizer audio processor, passing the
 * provided extraData as initialization data for the decoder
 * Returns the created context.
 */
SIA_VIR_HANDLE createHandle(JNIEnv *env, jstring appRootPath,
                            jstring configFilePathHrtf, jstring configFilePathCp);

/**
 * Releases the specified handle.
 */
void releaseHandle(SIA_VIR_HANDLE handle);

static const int MAJOR_VERSION_MASK = 0x00FF0000;
static const int MAJOR_VERSION_MASK_POS = 16;
static const int MINOR_VERSION_MASK = 0x0000FF00;
static const int MINOR_VERSION_MASK_POS = 8;
static const int REVISION_VERSION_MASK = 0x000000FF;
static const int REVISION_VERSION_MASK_POS = 0;


void planar(float *dest, float *src, int samplePerFrame, int channels);
void interleave(float *dest, float *src, int samplePerFrame, int channels);

LIBRARY_FUNC(jstring, VirtualizerGetVersion) {
  // LOGV("VirtualizerGetVersion() called");
  int version = sia_virtualizer_getVersion();
  int mejor = (version & MAJOR_VERSION_MASK) >> MAJOR_VERSION_MASK_POS;
  int minor = (version & MINOR_VERSION_MASK) >> MINOR_VERSION_MASK_POS;
  int revision = (version & REVISION_VERSION_MASK) >> REVISION_VERSION_MASK_POS;
  char versionStr[16];
  snprintf(versionStr, sizeof(versionStr), "V%d.%d.%d",
           mejor, minor, revision);

  return env->NewStringUTF(&versionStr[0]);
}

LIBRARY_FUNC(jlong, VirtualizerInitialize,
             jstring rootPath, jstring configFilePathHrtf,
             jstring configFilePathCp) {
  // LOGV("VirtualizerInitialize() called");
  return (jlong) createHandle(env, rootPath, configFilePathHrtf, configFilePathCp);
}

bool VirtualizerProcImp(SIA_VIR_HANDLE handle,
                        int status, int validSample, float *outputData, float *inputData,
                        int *zeroSndFlags);

LIBRARY_FUNC(jboolean, VirtualizerProc, jlong jHandle,
             jint status, jint validSamples, jobject outputData, jobject inputData,
             jintArray zeroSndFlags) {
  // LOGV("VirtualizerQueueInput() called inputSampleSize:%d", inputSampleSize);
  if (!jHandle) {
    LOGE("Handler must be non-NULL.");
    return false;
  }
  if (!inputData) {
    LOGE("Input and output buffers must be non-NULL.");
    return false;
  }
  if (validSamples < 0) {
    LOGE("Invalid input buffer size: %d.", validSamples);
    return false;
  }

  SIA_VIR_HANDLE handle = reinterpret_cast<SIA_VIR_HANDLE>(jHandle);
  float *inputBuffer = reinterpret_cast<float *>(env->GetDirectBufferAddress(inputData));
  float *outputBuffer = reinterpret_cast<float *>(env->GetDirectBufferAddress(outputData));
  int *zero_snd_flag = env->GetIntArrayElements(zeroSndFlags, NULL);

  bool ret = VirtualizerProcImp(handle, status, validSamples, outputBuffer, inputBuffer,
                                zero_snd_flag);

  env->ReleaseIntArrayElements(zeroSndFlags, zero_snd_flag, 0);

  return ret;
}

bool VirtualizerProcImp(SIA_VIR_HANDLE handle,
                        int status, int validSample, float *outputData, float *inputData,
                        int *zeroSndFlags) {
  float tempInputPlanerBuffer[FRAME_SIZE * INPUT_CH];
  float tempOutputBuffer[FRAME_SIZE * SIA_NUM_CH_VIR_OUTPUT];
  float *pOutput[SIA_NUM_CH_VIR_OUTPUT] = {tempOutputBuffer, tempOutputBuffer + FRAME_SIZE};

  planar(tempInputPlanerBuffer, inputData, FRAME_SIZE, INPUT_CH);

  int ret = sia_virtualizer_proc(handle, status, validSample, pOutput, tempInputPlanerBuffer,
                                 zeroSndFlags);
  if (ret != SIA_VIR_ERR_NO_ERROR) {
    LOGE("sia_virtualizer_proc error:%d", ret);
    return false;
  }

  interleave(outputData, tempOutputBuffer, FRAME_SIZE, SIA_NUM_CH_VIR_OUTPUT);

  return true;
}


LIBRARY_FUNC(jlong, VirtualizerReset, jlong jHandle) {
  // LOGV("VirtualizerReset() called");
  if (!jHandle) {
    LOGE("Invalid param : jHandle");
    return 0L;
  }
  SIA_VIR_HANDLE handle =
      reinterpret_cast<SIA_VIR_HANDLE>(jHandle);
  sia_virtualizer_reset(handle);

  return (jlong) jHandle;
}


void dump(char coefNo, unsigned char v[]) {
  LOGE(
      "   Coef%c check-sum              : %02X %02X %02X %02X %02X %02X %02X %02X  %02X %02X %02X %02X %02X %02X %02X %02X\n",
      coefNo,
      v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11], v[12], v[13], v[14],
      v[15]);

}

SIA_VIR_HANDLE createHandleImp(const char *appRootPath,
                               const char *configFilePathHrtf, const char *configFilePathCp);


SIA_VIR_HANDLE createHandle(JNIEnv *env, jstring appRootPath,
                            jstring configFilePathHrtf, jstring configFilePathCp) {

  const char *root = env->GetStringUTFChars(appRootPath, 0);
  const char *hrtf = env->GetStringUTFChars(configFilePathHrtf, 0);
  const char *cp = env->GetStringUTFChars(configFilePathCp, 0);

  SIA_VIR_HANDLE handle = createHandleImp(root, hrtf, cp);

  env->ReleaseStringUTFChars(appRootPath, root);
  env->ReleaseStringUTFChars(configFilePathHrtf, hrtf);
  env->ReleaseStringUTFChars(configFilePathCp, cp);

  return handle;
}

SIA_VIR_HANDLE createHandleImp(const char *appRootPath,
                               const char *configFilePathHrtf, const char *configFilePathCp) {
  SIA_VIR_HANDLE handle;
  int ret = sia_virtualizer_getHandle(&handle, appRootPath, configFilePathHrtf, configFilePathCp);
  if (ret != SIA_VIR_ERR_NO_ERROR) {
    LOGE("sia_virtualizer_getHandle error:%d", ret);
    return 0L;
  }
  // LOGV("sia_virtualizer_getHandle() handle:%p", handle);

  ret = sia_virtualizer_init(handle);
  if (ret != SIA_VIR_ERR_NO_ERROR) {
    LOGE("sia_virtualizer_init error:%d", ret);
    sia_virtualizer_close(handle);
    sia_virtualizer_freeHandle(&handle);
    return 0L;
  }

  ret = sia_virtualizer_reset(handle);
  if (ret != SIA_VIR_ERR_NO_ERROR) {
    LOGE("sia_virtualizer_reset error:%d", ret);
    sia_virtualizer_close(handle);
    sia_virtualizer_freeHandle(&handle);
    return 0L;
  }

  virtualizer_coef1_info coef1_info;
  ret = sia_virtualizer_getInfoCoef1(handle, &coef1_info);
  if (ret != SIA_VIR_ERR_NO_ERROR) {
    LOGE("sia_virtualizer_getInfoCoef1 error:%d", ret);
    LOGE("\n");
    LOGE("   Coef1 format name            : %s (%02X %02X %02X %02X)\n", coef1_info.name,
         coef1_info.name[0], coef1_info.name[1], coef1_info.name[2], coef1_info.name[3]);
    dump('1', coef1_info.sum);
    LOGE("   Coef1 YYYY.MM.DD.HH.MM       : %d.%d.%d.%d.%d\n", coef1_info.year, coef1_info.month,
         coef1_info.day, coef1_info.hour, coef1_info.min);
    LOGE("   Coef1 DistributerID          : %X\n", coef1_info.DistributerID);
  }
  virtualizer_coef2_info coef2_info;
  ret = sia_virtualizer_getInfoCoef2(handle, &coef2_info);
  if (ret != SIA_VIR_ERR_NO_ERROR) {
    LOGE("   Coef2 format name            : %s (%02X %02X %02X %02X)\n", coef2_info.name,
         coef2_info.name[0], coef2_info.name[1], coef2_info.name[2], coef2_info.name[3]);
    dump('2', coef1_info.sum);
    LOGE("   Coef2 DistributerID          : %X\n", coef2_info.DistributerID);
  }

  return handle;
}

LIBRARY_FUNC(void, VirtualizerRelease, jlong jHandle) {
  // LOGD("VirtualizerRelease() called");
  if (!jHandle) {
    LOGE("Invalid param : jHandle");
    return;
  }
  SIA_VIR_HANDLE handle =
      reinterpret_cast<SIA_VIR_HANDLE>(jHandle);
  releaseHandle(handle);

  return;
}

void releaseHandle(SIA_VIR_HANDLE handle) {
  if (0 != handle) {
    int ret = sia_virtualizer_close(handle);
    if (ret != SIA_VIR_ERR_NO_ERROR) {
      LOGE("sia_virtualizer_close error:%d", ret);
    }
    sia_virtualizer_freeHandle(&handle);
  }
}

static inline int planarIndex(int channelIdx, int channelMax, int sampleIdx, int sampleMax) {
  //   [channel, sample]
  //  [[0, 0], [0, 1], [0, 2] ,,, [0, sampleMax-1]
  //   [1, 0], [1, 1], [1, 2] ,,, [1, sampleMax-1]
  //   [2, 0], [2, 1], [2, 2] ,,, [2, sampleMax-1]
  //       ,,,
  //   [channelMax-1, 0], [channelMax-1, 1], [channelMax-1, 2] ,,, [channelMax-1, sampleMax-1]
  //  ]
  return (sampleMax * channelIdx) + sampleIdx;
}

static inline int interleaveIndex(int channelIdx, int channelMax, int sampleIdx, int sampleMax) {
  //   [channel, sample]
  //  [[0, 0], [1, 0], [2, 0] ,,, [channelMax-1, 0]
  //   [0, 1], [1, 1], [2, 1] ,,, [channelMax-1, 1]
  //   [0, 2], [1, 2], [2, 2] ,,, [channelMax-1, 2]
  //       ,,,
  //   [0, sampleMax-1], [1, sampleMax-1], [2, sampleMax-1] ,,, [channelMax -1, sampleMax-1]
  //  ]
  return (channelMax * sampleIdx) + channelIdx;
}

void planar(float *dest, float *src, int samplePerFrame, int channels) {
  for (int s = 0; s < samplePerFrame; s++) {
    for (int c = 0; c < channels; c++) {
      dest[planarIndex(c, channels, s, samplePerFrame)] =
          src[interleaveIndex(c, channels, s, samplePerFrame)];
    }
  }
}

void interleave(float *dest, float *src, int samplePerFrame, int channels) {
  for (int s = 0; s < samplePerFrame; s++) {
    for (int c = 0; c < channels; c++) {
      dest[interleaveIndex(c, channels, s, samplePerFrame)] =
          src[planarIndex(c, channels, s, samplePerFrame)];
    }
  }
}
