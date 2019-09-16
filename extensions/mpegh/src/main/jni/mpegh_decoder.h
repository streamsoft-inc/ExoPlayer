#ifndef EXTENSIONS_MPEGH_SRC_MAIN_JNI_MPEGH_DECODER_H_
#define EXTENSIONS_MPEGH_SRC_MAIN_JNI_MPEGH_DECODER_H_

#include <stdlib.h>
#include <memory>
#include <string>
#include "libSonyIA_mobile.h"
#include "alc.h"

namespace mpegh {

enum class MpeghDecoderError {
  kNoError = 0,
  kInvalidData,
  kError,
};

class MpeghDecoder {
 public:
  MpeghDecoder(const std::string &root_path,
               const std::string &config_path_hrtf,
               const std::string &config_path_cp);
  ~MpeghDecoder() = default;
  MpeghDecoder(const MpeghDecoder &rhs) = delete;
  MpeghDecoder &operator=(const MpeghDecoder &rhs) = delete;

  /*!
   * Set mpeg-h configulation (mhac container) for mha1
   *
   * [in] config_size   mhac configuration size
   * [in] config        pointer to mhac configuration
   * [return] true : success false : failed
   */
  bool Configure(size_t config_size, uint8_t *config);

  /*!
   * Decode mpeg-h data
   *
   * [in] input_buffer     pointer to mpeg-h sample
   * [in] input_size       size of one sample of mpeg-h data
   * [out] output_buffer   pointer to output buffer
   *                       output sample is always 1024sample/ch=2048sample
   * [out] output_size     output size of output_buffer
   * [in] is_end_of_stream if the input frame is end of the stream, set to true
   * [return] true : success false : failed
   */
  MpeghDecoderError Decode(uint8_t *input_buffer, int input_size,
                           float *output_buffer, int *output_size);

  /*!
   * Decode end of stream mpeg-h data
   *
   * [out] output_buffer   pointer to output buffer
   *                       output sample is always 1024sample/ch=2048sample
   * [out] output_size     output size of output_buffer
   * [return] true : success false : failed
   */
  MpeghDecoderError DecodeEos(float *output_buffer, int *output_size);

  /*!
   * Reset decoder's internal state
   */
  void ResetBuffer();

  /*!
   * Reset mpegh decoder configulation
   */
  bool ResetDecoder();

  static int GetOutputChannelCount();
  static int GetOutputFrequency();
  static int GetAmplifyGainDb();
  static int GetOutputSamplePerFrame();

  struct SiaMhaDeleter {
    void operator()(_sia_mha_struct_ *handle) const;
  };

  struct AlignedAllocDeleter {
    void operator()(uint8_t *data) const;
  };

 private:
  std::unique_ptr<_sia_mha_struct_, SiaMhaDeleter> p_context_;
  std::unique_ptr<uint8_t> p_mhac_config_;
  std::unique_ptr<uint8_t, AlignedAllocDeleter> p_alc_work_area_;
  bool is_initialized_;
  bool is_configured_;
  bool is_codec_mha1_;
  int multithread_delay_counter_;

  void PrintLastError();
  bool Initialize(const std::string &rootPath,
                  const std::string &configFilePathHrtf,
                  const std::string &configFilePathCp);
  bool AlcInit();
  bool ConfigureMhm1(uint8_t *inputBuffer, int inputSize);
  void *GetAlcHandle();
  void PrintAlcParam(const alc_config_t &alc_config);
};

} //  namespace mpegh

#endif  // EXTENSIONS_MPEGH_SRC_MAIN_JNI_MPEGH_DECODER_H_
