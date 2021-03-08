#include <stdlib.h>
#include <cstdint>
#include <cstring>
#include <string>
#include <android/log.h>
#include "mpegh_decoder.h"

#define LOG_TAG "mpegh_decoder"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                                             __VA_ARGS__))
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, \
                                             __VA_ARGS__))
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, \
                                             __VA_ARGS__))

namespace mpegh {

static const int kAmplifyGainDb = 1;
static const int kNumberOfChannels = 14;
static const int kSupportFrequency = 48000;
static const int kSamplePerFrame = 1024;
static const int kMhasFrameMaxSize = 19072;

static void interleave(float *dest, float *src, int samplePerFrame, int channels);

MpeghDecoder::MpeghDecoder()
    : is_initialized_(false), is_configured_(false), is_codec_mha1_(false), handle_(0) {
  LOGD("%s", __FUNCTION__);
  Initialize();
}

bool MpeghDecoder::Initialize() {
  LOGD("%s::sia_mhdr_getVersion()", __FUNCTION__);
  unsigned int version = sia_mhdr_getVersion();
  LOGI("SIA decoder lib version %2d.%02d.%02d\n",
       (version >> 16) & 0xFF, (version >> 8) & 0xFF, version & 0xFF);

  SIA_MHDR_HANDLE handle = nullptr;
  int uret = sia_mhdr_getHandle(&handle);
  if (SIA_ERR_NO_ERROR != uret) {
    LOGE("Failed to %s::sia_mhdr_getHandle(). ret:%d", __FUNCTION__, uret);
  } else {
    LOGD("%s::sia_mhdr_getHandle() ret:%d handle:%p", __FUNCTION__, uret, handle);
    handle_ = handle;
    is_initialized_ = true;
  }

  return is_initialized_;
}

bool MpeghDecoder::Configure(int config_size, uint8_t *config) {
  if (!is_initialized_ || is_configured_) return false;

  p_mhac_config_.reset(new uint8_t[config_size]);
  std::memcpy(static_cast<void *>(p_mhac_config_.get()),
              static_cast<const void *>(config),
              config_size);

  int ret = sia_mhdr_rawbsOpen(handle_,
                               p_mhac_config_.get(),
                               config_size);
  LOGD("%s::sia_mhdr_rawbsOpen(cxt:%p, mhac:%p, mhac_size:%d)",
       __FUNCTION__, handle_, p_mhac_config_.get(),
       static_cast<int>(config_size));
  if (ret) {
    LOGE("sia_mhdr_rawbsOpen failed ret:%d", ret);
    if (ret >= SIA_ERR_TYPE_1) {
      PrintLastError();
    }
    p_mhac_config_.reset();
    return false;
  }

  bool initResult = InitMhdr();
  if (!initResult) {
    sia_mhdr_rawbsClose(handle_);
    p_mhac_config_.reset();
    return false;
  }

  is_codec_mha1_ = true;
  is_configured_ = true;
  return is_configured_;
}

bool MpeghDecoder::ConfigureMhm1(uint8_t *inputBuffer, int inputSize) {
  if (!is_initialized_ || is_configured_) return false;

  auto bsOpenBuffer = std::make_unique<uint8_t[]>(kMhasFrameMaxSize);
  memset(bsOpenBuffer.get(), 0, kMhasFrameMaxSize * sizeof(uint8_t));
  memcpy(bsOpenBuffer.get(), inputBuffer, inputSize * sizeof(uint8_t));
  int ret = sia_mhdr_bsOpen(handle_,
                            bsOpenBuffer.get(),
                            kMhasFrameMaxSize);
  bsOpenBuffer.release();
  LOGD("%s::sia_mhdr_bsOpen(cxt:%p, buffer:%p, size:%d)",
       __FUNCTION__, handle_, inputBuffer,
       inputSize);
  if (ret) {
    LOGE("sia_mhdr_bsOpen failed ret:%d", ret);
    if (ret >= SIA_ERR_TYPE_1) {
      PrintLastError();
    }
    p_mhac_config_.reset();
    return false;
  }

  bool initResult = InitMhdr();
  if (!initResult) {
    sia_mhdr_bsClose(handle_);
    return false;
  }
  is_configured_ = true;
  return is_configured_;
}

bool MpeghDecoder::InitMhdr() {
  SIA_MHDR_PARAM param = {0};
  int ret = sia_mhdr_init(handle_, &param);
  LOGD("%s::sia_mhdr_init(cxt:%p, param:%p)",
       __FUNCTION__, handle_, &param);
  if (SIA_ERR_NO_ERROR != ret) {
    LOGE("sia_mhdr_init failed ret:%d", ret);
    if (ret >= SIA_ERR_TYPE_1) {
      PrintLastError();
    }
  }
  return (SIA_ERR_NO_ERROR == ret);
}

bool MpeghDecoder::ResetDecoder() {
  if (!is_initialized_ || !is_configured_) return false;
  int ret = sia_mhdr_close(handle_);
  LOGD("%s::sia_mhdr_close(cxt:%p)",
       __FUNCTION__, handle_);
  if (ret) {
    LOGE("sia_mhdr_close() error : %d", ret);
  }
  ret = sia_mhdr_rawbsClose(handle_);
  LOGD("%s::sia_mhdr_rawbsClose(cxt:%p)",
       __FUNCTION__, handle_);
  if (ret) {
    LOGE("sia_mhdr_rawbsClose() error : %d", ret);
  }
  p_mhac_config_.reset();

  is_configured_ = false;
  return true;
}

MpeghDecoderError MpeghDecoder::Decode(uint8_t *inputBuffer, int inputSize,
                                       float *outputBuffer, int *outputSize) {
  if (inputBuffer == NULL || outputBuffer == NULL) return MpeghDecoderError::kError;
  if (inputSize <= 0) return MpeghDecoderError::kError;
  if (!is_initialized_) return MpeghDecoderError::kError;

  if (!is_configured_) {
    bool ret = ConfigureMhm1(inputBuffer, inputSize);
    if (ret != true) return MpeghDecoderError::kError;
  }

  int is_last_frame = 0;
  MpeghDecoderError result = WriteFrame(&is_last_frame, inputBuffer, inputSize);
  if (MpeghDecoderError::kNoError != result) {
    return result;
  }
  int flagPostTbl[kNumberOfChannels];
  std::memset(flagPostTbl, 0, sizeof(int));

  result = ReadFrame(outputSize, outputBuffer, &is_last_frame, flagPostTbl);

  return result;
}

MpeghDecoderError
MpeghDecoder::WriteFrame(int *isLastFrame, uint8_t *inputBuffer, int inputSize) {
  int result = 0;
  if (is_codec_mha1_) {
    result = sia_mhdr_rawbsReadFrame(handle_,
                                     inputBuffer, inputSize, isLastFrame);
  } else {
    result = sia_mhdr_bsReadFrame(handle_,
                                  inputBuffer, inputSize, isLastFrame);
  }
  if (result) {
    if (is_codec_mha1_) {
      LOGE("sia_mhdr_rawbsReadFrame : %d", result);
    } else {
      LOGE("sia_mhdr_bsReadFrame : %d", result);
    }
    if (result >= SIA_ERR_TYPE_1) {
      PrintLastError();
    }
    return MpeghDecoderError::kError;
  }
  return MpeghDecoderError::kNoError;
}

MpeghDecoderError MpeghDecoder::ReadFrame(int *outputSize, float *outputBuffer,
                                          int *is_last_frame, int *pFlagPost) {

  float temp_buff_planar[kSamplePerFrame * kNumberOfChannels];
  float *p_pcm_out[kNumberOfChannels];
  for (int idx = 0; idx < kNumberOfChannels; ++idx) {
    p_pcm_out[idx] = temp_buff_planar + kSamplePerFrame * idx;
  }

  int result = sia_mhdr_procFrame(handle_, is_last_frame, p_pcm_out, pFlagPost);
  if (SIA_ERR_NO_ERROR != result) {
    LOGE("sia_mhdr_procFrame : %d", result);
    return MpeghDecoderError::kError;
  }
  interleave(outputBuffer, temp_buff_planar, kSamplePerFrame, kNumberOfChannels);
  *outputSize = kSamplePerFrame * kNumberOfChannels * sizeof(float);
  return MpeghDecoderError::kNoError;
}

void MpeghDecoder::ResetBuffer() {
  if (!is_initialized_ || !is_configured_) return;
  sia_mhdr_reset(handle_);
  LOGD("%s()", __FUNCTION__);
}

void MpeghDecoder::PrintLastError() {
  int error_code = 0;
  int error_detail = 0;
  sia_mhdr_getErrorDetail(handle_, &error_code, &error_detail);
  LOGE("Error_code:%d error_detail:%d", error_code, error_detail);
}

int MpeghDecoder::GetOutputChannelCount() { return kNumberOfChannels; }

int MpeghDecoder::GetOutputFrequency() { return kSupportFrequency; }

int MpeghDecoder::GetAmplifyGainDb() { return kAmplifyGainDb; }

int MpeghDecoder::GetOutputSamplePerFrame() { return kSamplePerFrame; }

static inline int
planarIndex(int channelIdx, int channelMax, int sampleIdx, int sampleMax) {
  //   [channel, sample]
  //  [[0, 0], [0, 1], [0, 2] ,,, [0, sampleMax-1]
  //   [1, 0], [1, 1], [1, 2] ,,, [1, sampleMax-1]
  //   [2, 0], [2, 1], [2, 2] ,,, [2, sampleMax-1]
  //       ,,,
  //   [channelMax-1, 0], [channelMax-1, 1], [channelMax-1, 2] ,,, [channelMax-1, sampleMax-1]
  //  ]
  return (sampleMax * channelIdx) + sampleIdx;
}

static inline int
interleaveIndex(int channelIdx, int channelMax, int sampleIdx, int sampleMax) {
  //   [channel, sample]
  //  [[0, 0], [1, 0], [2, 0] ,,, [channelMax-1, 0]
  //   [0, 1], [1, 1], [2, 1] ,,, [channelMax-1, 1]
  //   [0, 2], [1, 2], [2, 2] ,,, [channelMax-1, 2]
  //       ,,,
  //   [0, sampleMax-1], [1, sampleMax-1], [2, sampleMax-1] ,,, [channelMax -1, sampleMax-1]
  //  ]
  return (channelMax * sampleIdx) + channelIdx;
}

static void interleave(float *dest, float *src, int samplePerFrame, int channels) {
  for (int s = 0; s < samplePerFrame; s++) {
    for (int c = 0; c < channels; c++) {
      dest[interleaveIndex(c, channels, s, samplePerFrame)] =
          src[planarIndex(c, channels, s, samplePerFrame)];
    }
  }
}

}  // namespace mpegh
