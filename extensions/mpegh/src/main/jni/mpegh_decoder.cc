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

/**
 * Alc configuration
 */
static constexpr alc_config_t alc_default_config = {
  48000,
  0x0000,             // ramp_coef = 0dB
  0,                  // ramp_shift = 0dB
  ALC_DELAY_48,
  ALC_ATT_x48,
  ALC_REL_x48,
  (short)0x0000,      // comp_thresh = 0dBFS
  (short)0x8000,      // gate_thresh
  AMPLIFY_GAIN_DB     // front gain(dB)
};
static constexpr uint16_t alc_alignment = 32;  // 32byte = 256bit

MpeghDecoder::MpeghDecoder(const std::string& root_path,
                           const std::string& config_path_hrtf,
                           const std::string& config_path_cp)
                           : is_initialized_(false)
                           , is_opened_(false) {
  LOGD("%s", __FUNCTION__);
  int work_size = alc_get_worksize();
  if (work_size <= 0) return;

  uint8_t *work_area = NULL;
  int error = posix_memalign(reinterpret_cast<void**>(&work_area),
                             alc_alignment, work_size);
  if (error != 0) {
    LOGE("posix_memalign error : %d", error);
    return;
  } else if (reinterpret_cast<uint64_t>(work_area) % alc_alignment != 0) {
    LOGE("alc work area not aligned");
    return;
  }
  p_alc_work_area_.reset(work_area);
  LOGD("alc work area : %p", p_alc_work_area_.get());

  Initialize(root_path, config_path_hrtf, config_path_cp);
}

bool MpeghDecoder::Initialize(const std::string& rootPath,
                              const std::string& configFilePathHrtf,
                              const std::string& configFilePathCp) {
  LOGD("%s::sia_mha_getVersion()", __FUNCTION__);
  unsigned int version = sia_mha_getVersion();
  LOGI("SIA decoder lib version %2d.%02d.%02d\n",
      (version >> 16) & 0xFF, (version >> 8) & 0xFF, version & 0xFF);

  // Alc initialize
  bool alc_ret = AlcInit();
  if (alc_ret == false) {
      LOGE("Alc initialize failed");
      return false;
  }
  LOGD("alc work buffer size : %d", alc_get_worksize());
  LOGI("RootPath: %s", rootPath.c_str());
  LOGI("HrtfConfigPath: %s", configFilePathHrtf.c_str());
  LOGI("CpConfigPath: %s", configFilePathCp.c_str());

  _sia_mha_struct_* context;
  unsigned int uret = sia_mha_getHandle(&context, rootPath.c_str(),
                                        configFilePathHrtf.c_str(),
                                        configFilePathCp.c_str());
  LOGD("%s::sia_mha_getHandle() ret:%d cxt:%p", __FUNCTION__, uret, context);
  if (uret) {
      LOGE("Failed to allocate context.");
      return false;
  }
  p_context_.reset(context);

  is_initialized_ = true;
  return true;
}

bool MpeghDecoder::Open(size_t config_size, uint8_t *config) {

  if (!is_initialized_ || is_opened_) return false;

  p_mhac_config_.reset(new uint8_t[config_size]);
  std::memcpy(static_cast<void*>(p_mhac_config_.get()),
              static_cast<const void*>(config),
              config_size);

  int ret = sia_mha_rawbsOpen(p_context_.get(),
                              p_mhac_config_.get(),
                              config_size);
  LOGD("%s::sia_mha_rawbsOpen(cxt:%p, mhac:%p, mhac_size:%d)",
       __FUNCTION__, p_context_.get(), p_mhac_config_.get(),
      static_cast<int>(config_size));
  if (ret) {
      LOGE("sia_mha_rawbsOpen failed ret:%d", ret);
      if (ret >= SIA_ERR_TYPE_1) {
          PrintLastError();
      }
      p_mhac_config_.reset();
      return false;
  }

  SIA_MHA_PARAM param = {0};
  ret = sia_mha_init(p_context_.get(), &param);
  LOGD("%s::sia_mha_init(cxt:%p, param:%p)",
       __FUNCTION__, p_context_.get(), &param);
  if (ret) {
      LOGE("sia_mha_init failed ret:%d", ret);
      if (ret >= SIA_ERR_TYPE_1) {
          PrintLastError();
      }
      sia_mha_rawbsClose(p_context_.get());
      p_mhac_config_.reset();
      return false;
  }

  is_opened_ = true;
  return true;
}

bool MpeghDecoder::Close() {
  if (!is_initialized_ || !is_opened_) return false;
  int ret = sia_mha_close(p_context_.get());
  LOGD("%s::sia_mha_close(cxt:%p)",
       __FUNCTION__, p_context_.get());
  if (ret) {
    LOGE("sia_mha_close() error : %d", ret);
  }
  ret = sia_mha_rawbsClose(p_context_.get());
  LOGD("%s::sia_mha_rawbsClose(cxt:%p)",
       __FUNCTION__, p_context_.get());
  if (ret) {
    LOGE("sia_mha_bsClose() error : %d", ret);
  }
  p_mhac_config_.reset();

  is_opened_ = false;
  return true;
}

bool MpeghDecoder::Decode(uint8_t *inputBuffer, int inputSize,
                          float* outputBuffer) {
  int result = 0;
  int is_last_frame = 0;
  if (inputBuffer == NULL || outputBuffer == NULL) return false;
  if (inputSize <= 0) return false;
  if (!is_initialized_ || !is_opened_) return false;

  result = sia_mha_rawbsReadFrame(p_context_.get(),
                                  inputBuffer, inputSize, &is_last_frame);
  if (result) {
    LOGE("sia_mha_rawbsReadFrame : %d", result);
    if (result >= SIA_ERR_TYPE_1) {
      PrintLastError();
    }
      return false;
  }

  float temp_buff_planar[SAMPLE_PER_FRAME * NUMBER_OF_CHANNELS];
  float *p_pcm_out[NUMBER_OF_CHANNELS] =
          {temp_buff_planar, temp_buff_planar + SAMPLE_PER_FRAME};
  result = sia_mha_procFrame(p_context_.get(), &is_last_frame, p_pcm_out);
  if (result) {
    LOGE("sia_mha_procFrame : %d", result);
    return false;
  }

  int i, j;
  for (i=0; i < SAMPLE_PER_FRAME; i++) {
    for (j=0; j < NUMBER_OF_CHANNELS; j++) {
      outputBuffer[(i*NUMBER_OF_CHANNELS)+j] = *(p_pcm_out[j]+i);
    }
  }

  // Alc process
  result = alc_proc(GetAlcHandle(), outputBuffer, outputBuffer);
  if (result != 0) {
    LOGE("alc_proc() : error (code=%d)\n", result);
    return false;
  }

  return true;
}

void MpeghDecoder::Reset() {
  if (!is_initialized_ || !is_opened_) return;
  sia_mha_reset(p_context_.get());
  LOGD("%s()", __FUNCTION__);
}

void MpeghDecoder::PrintLastError() {
  int error_code = 0;
  int error_detail = 0;
  sia_mha_getErrorDetail(p_context_.get(), &error_code, &error_detail);
  LOGE("Error_code:%d error_detail:%d", error_code, error_detail);
}

bool MpeghDecoder::AlcInit() {
  int ret = alc_init(GetAlcHandle(), SAMPLE_PER_FRAME);
  LOGD("%s(handle:%p, samples:%d)",
       __FUNCTION__, GetAlcHandle(), SAMPLE_PER_FRAME);
  if (ret != 0) {
    LOGE("alc_init() : error (code=%d)\n", ret);
    return false;
  }

  ret = alc_set(GetAlcHandle(), const_cast<alc_config_t*>(&alc_default_config));
  LOGD("%s(handle:%p)",
       __FUNCTION__, GetAlcHandle());
  PrintAlcParam(alc_default_config);
  if (ret != 0) {
    LOGE("alc_set() : error (code=%d)\n", ret);
    return false;
  }

  return true;
}

void* MpeghDecoder::GetAlcHandle() {
  return static_cast<void*>(p_alc_work_area_.get());
}

void MpeghDecoder::PrintAlcParam(const alc_config_t& alc_config) {
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

void MpeghDecoder::SiaMhaDeleter::operator()(_sia_mha_struct_* handle) const {
  LOGD("%s::sia_mha_freeHandle(cxt:%p)", __FUNCTION__, handle);
  sia_mha_freeHandle(handle);
}

void MpeghDecoder::AlignedAllocDeleter::operator()(uint8_t* data) const {
  LOGD("%s(free(%p)", __FUNCTION__, data);
  free(data);
}