#ifndef EXTENSIONS_ALC_SRC_MAIN_JNI_ALC_H_
#define EXTENSIONS_ALC_SRC_MAIN_JNI_ALC_H_

#include <stdlib.h>
#include <memory>
#include <string>
#include "alc_component.h"

namespace alc {

    enum class AlcError {
        kNoError = 0,
        kInvalidData,
        kError,
    };

    class Alc {
    public:
        Alc();

        ~Alc() = default;

        Alc(const Alc &rhs) = delete;

        Alc &operator=(const Alc &rhs) = delete;

        AlcError ProcFrame(int offset, float *outputBuffer, int *outputSize);

        struct AlignedAllocDeleter {
            void operator()(uint8_t *data) const;
        };

    private:
        void PrepareWorkMemory();

        bool Initialize();

        void *GetAlcHandle();

        void PrintAlcParam(const alc_config_t &alc_config);

    private:
        std::unique_ptr<uint8_t, AlignedAllocDeleter> p_alc_work_area_;
        bool is_initialized_;
    };

} //  namespace alc

#endif  // EXTENSIONS_ALC_SRC_MAIN_JNI_ALC_H_
