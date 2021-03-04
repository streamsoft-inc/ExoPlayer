/******************************************************************************
 *
 *    alc_component.h
 *
 *    Header file for ALC component
 *
 *    (C) Copyright 2019 Sony Corporation All Rights Reserved.
 *
 ******************************************************************************/

#ifndef ALC_COMPONENT_H
#define ALC_COMPONENT_H

#ifdef RENAME_SYMBOL
#include "ALCApi_rename.h"
#endif

typedef struct {
    unsigned int fs;
    short        ramp_coef;
    short        ramp_shift;
    short        alc_delay;
    int          alc_att_time;
    int          alc_rel_time;
    short        alc_comp_thresh;
    short        alc_gate_thresh;
	int          gain;
} alc_config_t;

//#define ALC_WORKSIZE                 9086
//#define ALC_WORKSIZE                 17278
#define ALC_WORKSIZE                 39278

#define ALC_SUCCESS                     0
#define ALC_FAIL_NOINIT              (-1)
#define ALC_FAIL_ADDR                (-2)
#define ALC_FAIL_BLOCK_SIZE          (-3)
#define ALC_FAIL_FS                  (-4)
#define ALC_FAIL_ALLOC               (-5)
#define ALC_FAIL_FREE                (-6)

#define ALC_FAIL_ALC_DELAY           (-7)
#define ALC_FAIL_GAIN                (-8)

#define ALC_DELAY_44                  22
#define ALC_DELAY_48                  24

#define ALC_ATT_x44                0x6b231a90
#define ALC_ATT_x48                0x67d2ec9b
#define ALC_REL_x44                0x7fff67d3
#define ALC_REL_x48                0x7fff7430


#ifdef __cplusplus
extern "C" {
#endif
int alc_init(
    void *alc_work_ptr,
    unsigned int framelength
);
int alc_set(
    void *alc_work_ptr,
    alc_config_t *alc_config
);
int alc_proc(
    void *alc_work_ptr,
    float *input,
    float *output
);
int alc_get_version(void);
int alc_get_worksize(void);
#ifdef __cplusplus
}
#endif

#endif /* ALC_COMPONENT_H */
