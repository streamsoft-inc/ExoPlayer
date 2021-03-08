#include <stdlib.h>
#include <cstdint>
#include <cstring>
#include <string>
#include <android/log.h>
#include "alc.h"
#include "alc_component.h"

#define LOG_TAG "alc"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                                             __VA_ARGS__))
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, \
                                             __VA_ARGS__))
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, \
                                             __VA_ARGS__))

namespace alc {

static const int kAmplifyGainDb = 1;
static const int kNumberOfChannels = 2;
static const int kSupportFrequency = 48000;
static const int kSamplePerFrame = 1024;
static const int kDecoderMultithreadDelay = 1; // delay = kDecoderMultithreadDelay * 1024 sample
static const int kMhasFrameMaxSize = 19072;

/**
 * Alc configuration
 */
static constexpr alc_config_t alc_default_config = {
  48000,
  0x0000,             // ramp_coef = 0dB
  0,                  // ramp_shift = 0dB
  ALC_DELAY_48,
  ALC_ATT_x48,
  0x7AC6B85A,
  (short)0x0000,    // comp_thresh = 0dBFS
  (short)0x8000,    // gate_thresh
  kAmplifyGainDb     // front gain(dB)
};
static constexpr uint16_t alc_alignment = 32;  // 32byte = 256bit


static const int MAJOR_VERSION_MASK = 0x00FF0000;
static const int MAJOR_VERSION_MASK_SHIFT = 16;
static const int MINOR_VERSION_MASK = 0x00000FF00;
static const int MINOR_VERSION_MASK_SHIFT = 8;
static const int REVISION_VERSION_MASK = 0x000000FF;
static const int REVISION_VERSION_MASK_SHIFT = 0;

Alc::Alc()
        : is_initialized_(false) {
  int version = alc_get_version();
  int major = (version & MAJOR_VERSION_MASK) >> MAJOR_VERSION_MASK_SHIFT;
  int minor = (version & MINOR_VERSION_MASK) >> MINOR_VERSION_MASK_SHIFT;
  int revision = (version & REVISION_VERSION_MASK) >> REVISION_VERSION_MASK_SHIFT;
  LOGD("%s ver:%02X.%02X.%02X", __FUNCTION__, major, minor, revision);

  PrepareWorkMemory();
  is_initialized_ = Initialize();
}

void Alc::PrepareWorkMemory() {
  int work_size = alc_get_worksize();
  if (work_size <= 0) return;

  uint8_t *work_area = NULL;
  int error = posix_memalign(reinterpret_cast<void**>(&work_area),
                             alc_alignment, work_size);
  if (error != ALC_SUCCESS) {
    LOGE("posix_memalign error : %d", error);
    return;
  } else if (reinterpret_cast<uint64_t>(work_area) % alc_alignment != 0) {
    LOGE("alc work area not aligned");
    return;
  }
  p_alc_work_area_.reset(work_area);
  LOGD("alc work area : %p", p_alc_work_area_.get());
}

bool Alc::Initialize() {
  int ret = alc_init(GetAlcHandle(), kSamplePerFrame);
  LOGD("%s(handle:%p, samples:%d)",
       __FUNCTION__, GetAlcHandle(), kSamplePerFrame);
  if (ret != ALC_SUCCESS) {
    LOGE("alc_init() : error (code=%d)\n", ret);
    return false;
  }

  ret = alc_set(GetAlcHandle(),
                const_cast<alc_config_t *>(&alc_default_config));
  LOGD("%s(handle:%p)",
       __FUNCTION__, GetAlcHandle());
  PrintAlcParam(alc_default_config);
  if (ret != ALC_SUCCESS) {
    LOGE("alc_set() : error (code=%d)\n", ret);
    return false;
  }
  return true;
}

void *Alc::GetAlcHandle() {
  return static_cast<void *>(p_alc_work_area_.get());
}

void Alc::PrintAlcParam(const alc_config_t& alc_config) {
  LOGI("AlcParam");
  LOGI("\tfs:%d", alc_config.fs);
  LOGI("\tramp_coef:%d", alc_config.ramp_coef);
  LOGI("\tramp_shift:%d", alc_config.ramp_shift);
  LOGI("\tdelay:%d", alc_config.alc_delay);
  LOGI("\tatt_time:%x", alc_config.alc_att_time);
  LOGI("\trel_time:%x", alc_config.alc_rel_time);
  LOGI("\tcomp_thresh:%hx", alc_config.alc_comp_thresh);
  LOGI("\tgate_thresh:%hx", alc_config.alc_gate_thresh);
  LOGI("\tgain:%d", alc_config.gain);
}

AlcError Alc::ProcFrame(int offset, float *ioBuffer, int *ioSize) {
  if (offset < 0) return AlcError::kError;
  if (ioBuffer == NULL) return AlcError::kError;
  if (!is_initialized_) return AlcError::kError;

  float* io = &ioBuffer[offset];

  // Alc process
  int result = alc_proc(GetAlcHandle(), io, io);
  if (result != ALC_SUCCESS) {
      LOGE("alc_proc() : error (code=%d)\n", result);
      return AlcError::kError;
  }

  int frameSize = kSamplePerFrame * kNumberOfChannels + offset;
  *ioSize = frameSize * sizeof(float);

  return AlcError::kNoError;
}

void Alc::AlignedAllocDeleter::operator()(uint8_t *data) const {
  LOGD("%s(free(%p)", __FUNCTION__, data);
  free(data);
}


}  // namespace alc
