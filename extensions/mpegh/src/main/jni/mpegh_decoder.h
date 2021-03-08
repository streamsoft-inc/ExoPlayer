#ifndef EXTENSIONS_MPEGH_SRC_MAIN_JNI_MPEGH_DECODER_H_
#define EXTENSIONS_MPEGH_SRC_MAIN_JNI_MPEGH_DECODER_H_

#include <stdlib.h>
#include <memory>
#include <string>
#include "libSonyIA_mobile.h"

namespace mpegh {

enum class MpeghDecoderError {
  kNoError = 0,
  kInvalidData,
  kError,
};

class MpeghDecoder {
public:
  MpeghDecoder();

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
  bool Configure(int config_size, uint8_t *config);

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

private:
  void PrintLastError();

  bool Initialize();

  bool InitMhdr();

  bool ConfigureMhm1(uint8_t *inputBuffer, int inputSize);

  MpeghDecoderError ReadFrame(int *outputSize, float *outputBuffer,
                              int *is_last_frame, int *pFlagPoost);

  MpeghDecoderError WriteFrame(int *isLastFrame, uint8_t *inputBuffer, int inputSize);

private:
  std::unique_ptr<uint8_t[]> p_mhac_config_;
  bool is_initialized_;
  bool is_configured_;
  bool is_codec_mha1_;
  SIA_MHDR_HANDLE handle_;

};

} //  namespace mpegh

#endif  // EXTENSIONS_MPEGH_SRC_MAIN_JNI_MPEGH_DECODER_H_
