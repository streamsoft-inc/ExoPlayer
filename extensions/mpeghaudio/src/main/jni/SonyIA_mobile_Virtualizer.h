#pragma once

typedef struct _sia_virtualizer_struct_ *SIA_VIR_HANDLE;
struct virtualizer_Param
{
	unsigned int frame_length;
	unsigned int coef_length;
	unsigned int num_input_ch;
	unsigned int num_output_ch;
};

struct virtualizer_coef1_info
{
	signed char name[5];
	unsigned char sum[17];
	int year;
	int month;
	int day;
	int hour;
	int min;
	unsigned int DistributerID;
};

struct virtualizer_coef2_info
{
	signed char name[5];
	unsigned int DistributerID;
	unsigned char sum[17];
};

int sia_virtualizer_getHandle(SIA_VIR_HANDLE* phSiaVir, const char* appRootPath, const char* fnameCfCoef1, const char* fnameCfCoef2);
int sia_virtualizer_freeHandle(SIA_VIR_HANDLE* phSiaVir);
int sia_virtualizer_init(SIA_VIR_HANDLE hSiaVir);
int sia_virtualizer_getParam(SIA_VIR_HANDLE hSiaVir, virtualizer_Param*);
int sia_virtualizer_close(SIA_VIR_HANDLE hSiaVir);
int sia_virtualizer_reset(SIA_VIR_HANDLE hSiaVir);
int sia_virtualizer_proc(SIA_VIR_HANDLE hSiaVir, int status, unsigned int validSamples, float** pOutput, const float* pInput, int *zero_snd_flag);
int sia_virtualizer_getVersion();
int sia_virtualizer_getVersionLib1();
int sia_virtualizer_getVersionLib2();
int sia_virtualizer_getInfoCoef1(SIA_VIR_HANDLE hSiaVir, virtualizer_coef1_info *coef1_version);
int sia_virtualizer_getInfoCoef2(SIA_VIR_HANDLE hSiaVir, virtualizer_coef2_info *coef2_version);
int sia_virtualizer_getErrorDetail(SIA_VIR_HANDLE hSiaVir, int *errorCode, int *errorDetail);

#define SIA_NUM_CH_VIR_OUTPUT		(2)
#define FRAME_SIZE					(1024)
#define INPUT_CH					(14)
#define SIA_MAX_FILE_PATH_LEN		(1024)


#define SIA_VIR_STATUS_START		(0)
#define SIA_VIR_STATUS_gHANDLE		(1)
#define SIA_VIR_STATUS_INIT			(2)
#define SIA_VIR_STATUS_CLOSE		(3)

#define SIA_VIR_FRAME_MIDDLE		(0)
#define SIA_VIR_FRAME_START			(1)
#define SIA_VIR_FRAME_END			(2)


#define SIA_VIR_ERR_NO_ERROR		(0)
#define SIA_VIR_ERR_MEM_ALLOC		(-1)
#define SIA_VIR_ERR_GET_HANDLE		(-2)
#define SIA_VIR_ERR_NULL_HANDLE		(-3)
#define SIA_VIR_ERR_STATUS			(-4)
#define SIA_VIR_ERR_FRAME_STATUS	(-5)
#define SIA_VIR_ERR_NO_COEF_FILE	(-6)
#define SIA_VIR_ERR_IVPT			(-100)
#define SIA_VIR_ERR_HPEQ			(-200)
#define SIA_VIR_ERR_TIMER			(-300)
