/***********************************************************************************
 Unified library of MPEG-H 3D Audio Core Decoder, GE Renderer and Virtualizer
 ***********************************************************************************/

#ifndef __LIB_SIA_MOBILE_H__
#define __LIB_SIA_MOBILE_H__

#ifdef __cplusplus
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
#define SIA_ERR_INVALID_API_CALL 8

typedef struct _sia_mhdr_struct_ *SIA_MHDR_HANDLE;

typedef struct _sia_mhdr_param_ {
		int reserved[16];
} SIA_MHDR_PARAM;


EXTERNC unsigned int sia_mhdr_getVersion(void);
EXTERNC int sia_mhdr_getHandle(SIA_MHDR_HANDLE *phSiaDec);
EXTERNC int sia_mhdr_freeHandle(SIA_MHDR_HANDLE *phSiaDec);
EXTERNC int sia_mhdr_init(SIA_MHDR_HANDLE hSiaDec, SIA_MHDR_PARAM *pParam);
EXTERNC int sia_mhdr_close(SIA_MHDR_HANDLE hSiaDec);
EXTERNC int sia_mhdr_reset(SIA_MHDR_HANDLE hSiaDec);

EXTERNC int sia_mhdr_procFrame(SIA_MHDR_HANDLE hSiaDec, int *isLastFrame, float **pOutput, int *pFlagPost);

EXTERNC int sia_mhdr_fOpen(SIA_MHDR_HANDLE hSiaDec, char *inFile);
EXTERNC int sia_mhdr_fReadFrame(SIA_MHDR_HANDLE hSiaDec, int *isLastFrame);
EXTERNC int sia_mhdr_fClose(SIA_MHDR_HANDLE hSiaDec);

EXTERNC int sia_mhdr_bsOpen(SIA_MHDR_HANDLE hSiaDec, unsigned char *bs_in, long bs_size);
EXTERNC int sia_mhdr_bsReadFrame(SIA_MHDR_HANDLE hSiaDec, unsigned char *bs_in, long bs_size, int *isLastFrame);
EXTERNC int sia_mhdr_bsClose(SIA_MHDR_HANDLE hSiaDec);

EXTERNC void sia_mhdr_getErrorDetail(SIA_MHDR_HANDLE hSiaDec, int *pErrorCode, int *pErrorDetail);
EXTERNC unsigned int sia_mhdr_getNumObjects(SIA_MHDR_HANDLE hSiaDec);
EXTERNC unsigned int sia_mhdr_getTotalNumFrames(SIA_MHDR_HANDLE hSiaDec);
EXTERNC unsigned int sia_mhdr_getCurrentFrameIndex(SIA_MHDR_HANDLE hSiaDec);
EXTERNC int sia_mhdr_setFrameIndex(SIA_MHDR_HANDLE hSiaDec, unsigned int index);

EXTERNC int sia_mhdr_rawbsOpen(SIA_MHDR_HANDLE hSiaDec, unsigned char *bs_in, long bs_size);
EXTERNC int sia_mhdr_rawbsReadFrame(SIA_MHDR_HANDLE hSiaDec, unsigned char *bs_in, long bs_size, int *isLastFrame);
EXTERNC int sia_mhdr_rawbsClose(SIA_MHDR_HANDLE hSiaDec);

EXTERNC unsigned int sia_mhdr_getRelVersion(void);
#endif /* __LIB_SIA_MOBILE_H__ */
