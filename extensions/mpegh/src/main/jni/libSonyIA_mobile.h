/***********************************************************************************
 Unified library of MPEG-H 3D Audio Core Decoder, GE Renderer and Virtualizer
 ***********************************************************************************/

#ifndef __LIB_SIA_MOBILE_H__
#define __LIB_SIA_MOBILE_H__

#ifdef __cplusplus
//#define EXTERNC extern "C"
#if defined(__WIN32__)
#define EXTERNC extern "C" __declspec(dllexport) 
#elif defined(__GNUC__) && __GNUC__ >= 4
#define EXTERNC extern "C" __attribute__ ((visibility("default")))
#elif defined(__GNUC__) && __GNUC__ >= 2
#define EXTERNC extern "C" __declspec(dllexport)
#else
#define EXTERNC extern "C"
#endif
#else
#define EXTERNC
#endif

#define SIA_ERR_NO_ERROR         0
#define SIA_ERR_MEM_ALLOC        1
#define SIA_ERR_GET_HANDLE       2
#define SIA_ERR_NULL_HANDLE      3
#define SIA_ERR_TYPE_1           4
#define SIA_ERR_TYPE_2           5
#define SIA_ERR_TYPE_3           6
#define SIA_ERR_TYPE_4           7

typedef struct _sia_mha_struct_ *SIA_MHA_HANDLE;

typedef struct _sia_mha_param_ {
  int reserved[16];
} SIA_MHA_PARAM;


EXTERNC unsigned int sia_mha_getVersion(void);
EXTERNC int sia_mha_getHandle(SIA_MHA_HANDLE *phSiaDec, const char *appRootPath, const char *fnameCoef1, const char *fnameCoef2);
EXTERNC void sia_mha_freeHandle(SIA_MHA_HANDLE hSiaDec);
EXTERNC int sia_mha_init(SIA_MHA_HANDLE hSiaDec, SIA_MHA_PARAM *pParam);
EXTERNC int sia_mha_close(SIA_MHA_HANDLE hSiaDec);
EXTERNC int sia_mha_reset(SIA_MHA_HANDLE hSiaDec);

EXTERNC int sia_mha_procFrame(SIA_MHA_HANDLE hSiaDec, int *isLastFrame, float **pOutput);

EXTERNC int sia_mha_fOpen(SIA_MHA_HANDLE hSiaDec, char *inFile);
EXTERNC int sia_mha_fReadFrame(SIA_MHA_HANDLE hSiaDec, int *isLastFrame);
EXTERNC int sia_mha_fClose(SIA_MHA_HANDLE hSiaDec);

EXTERNC int sia_mha_bsOpen(SIA_MHA_HANDLE hSiaDec, unsigned char *bs_in, long bs_size);
EXTERNC int sia_mha_bsReadFrame(SIA_MHA_HANDLE hSiaDec, unsigned char *bs_in, long bs_size, int *isLastFrame);
EXTERNC int sia_mha_bsClose(SIA_MHA_HANDLE hSiaDec);

EXTERNC void sia_mha_getErrorDetail(SIA_MHA_HANDLE hSiaDec, int *pErrorCode, int *pErrorDetail);
EXTERNC unsigned int sia_mha_getNumObjects(SIA_MHA_HANDLE hSiaDec);
EXTERNC unsigned int sia_mha_getTotalNumFrames(SIA_MHA_HANDLE hSiaDec);
EXTERNC unsigned int sia_mha_getCurrentFrameIndex(SIA_MHA_HANDLE hSiaDec);
EXTERNC int sia_mha_setFrameIndex(SIA_MHA_HANDLE hSiaDec, unsigned int index);

EXTERNC int sia_mha_rawbsOpen(SIA_MHA_HANDLE hSiaDec, unsigned char *bs_in, long bs_size);
EXTERNC int sia_mha_rawbsReadFrame(SIA_MHA_HANDLE hSiaDec, unsigned char *bs_in, long bs_size, int *isLastFrame);
EXTERNC int sia_mha_rawbsClose(SIA_MHA_HANDLE hSiaDec);

EXTERNC int sia_coef1_getVersion(SIA_MHA_HANDLE hSiaDec, signed char *name, unsigned char *sum, 
								int *year, int *month, int *day, int *hour, int *min);
EXTERNC int sia_coef2_getVersion(SIA_MHA_HANDLE hSiaDec, signed char *name, unsigned char *sum);
#endif /* __LIB_SIA_MOBILE_H__ */
