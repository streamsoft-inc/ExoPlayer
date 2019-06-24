#ifndef EXTENSIONS_MPEGH_SRC_MAIN_JNI_MPEGH_DECODER_H_
#define EXTENSIONS_MPEGH_SRC_MAIN_JNI_MPEGH_DECODER_H_

#include <stdlib.h>
#include <memory>
#include <string>
#include "libSonyIA_mobile.h"
#include "alc.h"

static const int AMPLIFY_GAIN_DB      = 3;
static const int NUMBER_OF_CHANNELS   = 2;
static const int SUPPORT_FREQUENCY    = 48000;
static const int SAMPLE_PER_FRAME     = 1024;
static const int SAMPLE_SIZE          = 4;    // single-presision float
static const int OUTPUT_SIZE          = SAMPLE_PER_FRAME * NUMBER_OF_CHANNELS *
                                        SAMPLE_SIZE;

class MpeghDecoder {
 public:
    MpeghDecoder(const std::string& root_path,
                 const std::string& config_path_hrtf,
                 const std::string& config_path_cp);
    ~MpeghDecoder() = default;
    MpeghDecoder(const MpeghDecoder& rhs) = delete;
    MpeghDecoder& operator=(const MpeghDecoder& rhs) = delete;

   /*!
   * Set mpeg-h configulation (mhac container)
   *
   * [in] config_size   mhac configuration size
   * [in] config        pointer to mhac configuration
   * [return] true : success false : failed
   */
    bool Open(size_t config_size, uint8_t *config);

   /*!
   * Decode mpeg-h data
   *
   * [in] input_buffer    pointer to mpeg-h sample
   * [in] input_size      size of one sample of mpeg-h data
   * [out] output_buffer  pointer to output buffer
   *                      output sample is always 1024sample/ch=2048sample
   * [return] true : success false : failed
   */
    bool Decode(uint8_t *input_buffer, int input_size,
                float* output_buffer);

   /*!
   * Reset decoder's internal state
   */
    void Reset();

   /*!
   * Close mpegh decoder and reset mpeg-h configulation
   */
    bool Close();

    struct SiaMhaDeleter {
      void operator()(_sia_mha_struct_* handle) const;
    };

    struct AlignedAllocDeleter {
      void operator()(uint8_t* data) const;
    };

 private:
    std::unique_ptr<_sia_mha_struct_, SiaMhaDeleter> p_context_;
    std::unique_ptr<uint8_t> p_mhac_config_;
    std::unique_ptr<uint8_t, AlignedAllocDeleter> p_alc_work_area_;
    bool is_initialized_;
    bool is_opened_;
    void PrintLastError();
    bool Initialize(const std::string& rootPath,
                    const std::string& configFilePathHrtf,
                    const std::string& configFilePathCp);
    bool AlcInit();
    void* GetAlcHandle();
    void PrintAlcParam(const alc_config_t& alc_config);
};

#endif  // EXTENSIONS_MPEGH_SRC_MAIN_JNI_MPEGH_DECODER_H_
