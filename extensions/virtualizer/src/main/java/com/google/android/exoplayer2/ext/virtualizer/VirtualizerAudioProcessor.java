/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.ext.virtualizer;


import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.util.LibraryLoader;
import com.google.android.exoplayer2.util.Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * An {@link AudioProcessor} that uses {@code VirtualizerAudioProcessor} to provide rendering of
 * 3D surround sound.
 */
public final class VirtualizerAudioProcessor implements AudioProcessor {

    static {
        ExoPlayerLibraryInfo.registerModule("goog.exo.virtualizer");
    }

    private static final String TAG = "Virtualizer";
    private static final LibraryLoader LOADER = new LibraryLoader("virtualizer");

    private static final int FLOAT_NAN_AS_INT = Float.floatToIntBits(Float.NaN);
    private static final double PCM_32_BIT_INT_TO_PCM_32_BIT_FLOAT_FACTOR = 1.0 / 0x7FFFFFFF;

    private static final int FRAMES_PER_OUTPUT_BUFFER = 10240; // ToDo : Need to optimize
    private static final int OUTPUT_CHANNEL_COUNT = 2;
    private static final int OUTPUT_FRAME_SIZE = FRAMES_PER_OUTPUT_BUFFER * OUTPUT_CHANNEL_COUNT * Float.BYTES; // single-precision float

    private String appRootPath;
    private String configFilePathHrtf;
    private String configFilePathCp;
    private int sampleRateHz;
    private int channelCount;
    private int resampleRateHz;
    private @C.PcmEncoding int sourceEncoding;
    private boolean inputEnded;
    private long nativeHandler;
    private boolean initialized;

    private ByteBuffer inputBuffer;
    private ByteBuffer tempFloatBuffer;
    private ByteBuffer outputBuffer;

    public static void setLibraries(String... libraries)  {
        LOADER.setLibraries(libraries);
    }

    /** Creates a new Virtualizer audio processor. */
    public VirtualizerAudioProcessor(String appRootPath) {
        Log.d(TAG, "VirtualizerAudioPorcessor() root:" + appRootPath);
        if (!isAvailable()) {
            // ToDo : Add Exception
        }
        this.appRootPath = appRootPath;
        configFilePathHrtf = "files/com.sony.immersive-audio/coef/com.sony.360ra.hrtf2.config";
        configFilePathCp = "files/com.sony.immersive-audio/coef/com.sony.360ra.cp.config";
        sampleRateHz = Format.NO_VALUE;
        channelCount = Format.NO_VALUE;
        sourceEncoding = C.ENCODING_INVALID;
        inputBuffer = EMPTY_BUFFER;
        outputBuffer = EMPTY_BUFFER;
        nativeHandler = 0;
        initialized = false;
    }

    public static boolean isAvailable() {
        //Log.d(TAG, "isAvailable() called");
        return LOADER.isAvailable();
    }

    @Override
    public synchronized boolean configure(
            int sampleRateHz, int channelCount, @C.Encoding int encoding)
            throws UnhandledFormatException {
        Log.d(TAG, "configure() sampleRateHz:" + sampleRateHz +
                " channelCount:" + channelCount + " encoding:" + encoding);
        if (encoding == C.ENCODING_PCM_FLOAT) {
            throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
        }
        if (this.sampleRateHz == sampleRateHz
                && this.channelCount == channelCount
                && sourceEncoding == encoding) {
            return false;
        }
        this.sampleRateHz = sampleRateHz;
        this.channelCount = channelCount;
        sourceEncoding = encoding;

        if (nativeHandler != 0) VirtualizerReset(nativeHandler);
        nativeHandler = VirtualizerInitialize(sampleRateHz, appRootPath, configFilePathHrtf, configFilePathCp);
        if (nativeHandler == 0) {
            Log.e(TAG, "VirtualizerInitialize() Error");
            throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
        }
        resampleRateHz = VirtualizerGetSampleRate(nativeHandler);
        tempFloatBuffer = ByteBuffer.allocateDirect(OUTPUT_FRAME_SIZE).order(ByteOrder.nativeOrder());
        initialized = true;
        return true;
    }

    @Override
    public boolean isActive()
    {
        Log.v(TAG, "IaActive() :" + ((nativeHandler!=0)?"true":"false"));
        return nativeHandler != 0;
    }

    @Override
    public int getOutputChannelCount() {
        Log.v(TAG, "getOutputChannelCount() :" + OUTPUT_CHANNEL_COUNT);
        return OUTPUT_CHANNEL_COUNT;
    }

    @Override
    public int getOutputEncoding() {
        Log.v(TAG, "getOutputEncoding() :" + C.ENCODING_PCM_16BIT);
        return C.ENCODING_PCM_16BIT;
    }

    @Override
    public int getOutputSampleRateHz() {
        if (!initialized) return 0;
        Log.v(TAG, "getOutputSampleRateHz() sampleRate:" + resampleRateHz);
        return resampleRateHz;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!initialized) {
            Log.e(TAG, "queueInput Error not initialized");
            return;
        }
        int position = inputBuffer.position();
        int limit = inputBuffer.limit();
        int size = limit - position;
        int sampleByte = 0;
        int resampledSize = 0;

        switch (sourceEncoding) {
            case C.ENCODING_PCM_32BIT:
                resampledSize = size;
                sampleByte = 4;
                break;
            case C.ENCODING_PCM_24BIT:
                resampledSize = size/3*4;
                sampleByte = 3;
                break;
            case C.ENCODING_PCM_16BIT:
                resampledSize = size/2*4;
                sampleByte = 2;
                break;
            case C.ENCODING_PCM_8BIT:
                resampledSize = size*4;
                sampleByte = 1;
                break;
        }
        if (sourceEncoding == C.ENCODING_PCM_32BIT) {
        }
        if (this.inputBuffer.capacity() < resampledSize) {
            this.inputBuffer = EMPTY_BUFFER;
            this.inputBuffer = ByteBuffer.allocateDirect(resampledSize).order(ByteOrder.nativeOrder());
        } else {
            this.inputBuffer.clear();
        }
        this.inputBuffer.limit(resampledSize);

        if (sourceEncoding == C.ENCODING_PCM_32BIT) {
            for (int i = position; i < limit; i += 4) {
                int pcm32BitInteger =
                        (inputBuffer.get(i) & 0xFF)
                                | ((inputBuffer.get(i + 1) & 0xFF) << 8)
                                | ((inputBuffer.get(i + 2) & 0xFF) << 16)
                                | ((inputBuffer.get(i + 3) & 0xFF) << 24);
                writePcm32BitFloat(pcm32BitInteger, this.inputBuffer);
            }
        } else if (sourceEncoding == C.ENCODING_PCM_24BIT){
            for (int i = position; i < limit; i += 3) {
                int pcm32BitInteger =
                        ((inputBuffer.get(i) & 0xFF) << 8)
                                | ((inputBuffer.get(i + 1) & 0xFF) << 16)
                                | ((inputBuffer.get(i + 2) & 0xFF) << 24);
                writePcm32BitFloat(pcm32BitInteger, this.inputBuffer);
            }
        } else if (sourceEncoding == C.ENCODING_PCM_16BIT) {
            for (int i = position; i < limit; i += 2) {
                int pcm32BitInteger =
                        ((inputBuffer.get(i) & 0xFF) << 16)
                                | ((inputBuffer.get(i + 1) & 0xFF) << 24);
                writePcm32BitFloat(pcm32BitInteger, this.inputBuffer);
            }
        } else if (sourceEncoding == C.ENCODING_PCM_8BIT) {
            for (int i = position; i < limit; i += 1) {
                int pcm32BitInteger =
                        ((inputBuffer.get(i) & 0xFF) << 24);
                writePcm32BitFloat(pcm32BitInteger, this.inputBuffer);
            }
        }
        this.inputBuffer.flip();

        boolean ret = VirtualizerQueueInput(this.nativeHandler, this.inputBuffer, resampledSize/Float.BYTES);
        inputBuffer.position(position + size);
        if (ret == false) {
            Log.e(TAG, "queueInput() called size:" + size + " input_size:" + resampledSize/Float.BYTES);
        }

    }

    @Override
    public void queueEndOfStream() {
        if (!initialized) {
            Log.e(TAG, "queueEndOfStream Error not initialized");
            return;
        }
        Log.v(TAG, "queueEndOfStream() called");
        inputEnded = true;
        //gvrAudioSurround.triggerProcessing();
    }

    @Override
    public ByteBuffer getOutput() {
        if (!initialized) {
            Log.e(TAG, "getOutput Error not initialized");
            return EMPTY_BUFFER;
        }

        tempFloatBuffer.clear();
        outputBuffer.clear();
        int outputSampleCount = VirtualizerGetOutput(this.nativeHandler, tempFloatBuffer, OUTPUT_FRAME_SIZE/4);
        if (outputSampleCount == 0) {
            outputBuffer.limit(0);
            return outputBuffer;
        }

        int outputByteFloat = outputSampleCount * Float.BYTES;
        tempFloatBuffer.limit(outputByteFloat);

        int outputByteInt16 = outputByteFloat/2;
        if (this.outputBuffer.capacity() < outputByteInt16) {
            this.outputBuffer = EMPTY_BUFFER;
            this.outputBuffer = ByteBuffer.allocateDirect(outputByteInt16).order(ByteOrder.nativeOrder());
        } else {
            this.outputBuffer.clear();
        }
        this.outputBuffer.limit(outputByteInt16);

        while(tempFloatBuffer.hasRemaining()) {
            float fsample = tempFloatBuffer.getFloat();
            short isample;
            fsample = fsample * 32768;
            fsample = Math.round(fsample);
            if( fsample > 32767 ) fsample = 32767;
            if( fsample < -32768 ) fsample = -32768;
            isample = (short) fsample;
            outputBuffer.putShort(isample);
        }
        outputBuffer.flip();

        return outputBuffer;
    }

    @Override
    public boolean isEnded() {
        Log.v(TAG, "isEnded() called");
        return inputEnded;
    }

    @Override
    public void flush() {
        Log.v(TAG, "flush() called");
        VirtualizerReset(nativeHandler);
        inputEnded = false;
    }

    @Override
    public synchronized void reset() {
        Log.v(TAG, "reset() called");
        inputEnded = false;
        VirtualizerRelease(nativeHandler);
        initialized = false;
        nativeHandler = 0;
    }

    /**
     * Converts the provided 32-bit integer to a 32-bit float value and writes it to {@code buffer}.
     *
     * @param pcm32BitInt The 32-bit integer value to convert to 32-bit float in [-1.0, 1.0].
     * @param buffer The output buffer.
     */
    private static void writePcm32BitFloat(int pcm32BitInt, ByteBuffer buffer) {
        float pcm32BitFloat = (float) (PCM_32_BIT_INT_TO_PCM_32_BIT_FLOAT_FACTOR * pcm32BitInt);
        int floatBits = Float.floatToIntBits(pcm32BitFloat);
        if (floatBits == FLOAT_NAN_AS_INT) {
            floatBits = Float.floatToIntBits((float) 0.0);
        }
        buffer.putInt(floatBits);
    }

    private native long VirtualizerInitialize(int fs, String rootPath, String configFilePathHrtf, String configFilePathCp);
    private native boolean VirtualizerQueueInput(long context, ByteBuffer inputData, int inputSize);
    private native int VirtualizerGetOutput(long context, ByteBuffer outputData, int outputCapacity);
    private native int VirtualizerGetChannelCount(long context);
    private native int VirtualizerGetSampleRate(long context);
    private native long VirtualizerReset(long context);
    private native void VirtualizerRelease(long context);

}
