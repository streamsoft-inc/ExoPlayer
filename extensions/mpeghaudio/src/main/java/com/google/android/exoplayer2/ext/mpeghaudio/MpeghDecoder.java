/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.mpeghaudio;

import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.DecoderException;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import com.google.android.exoplayer2.decoder.SimpleOutputBuffer;
import com.google.android.exoplayer2.util.MimeTypes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

//--------------------------------------------------------------------//
// IA decoder impl
//--------------------------------------------------------------------//

/**
 * MPEG-H 3D audio decoder.
 */
/* package */ final class MpeghDecoder extends
    SimpleDecoder<DecoderInputBuffer, SimpleOutputBuffer, DecoderException> {

  static private String TAG = "MpeghDecoderClass";
  // Space for 32 ms of 48 kHz 14 channel 32-bit Float audio.
  private static final int OUTPUT_BUFFER_SIZE_SINGLE_PRECISION_FLOAT = 32 * 48 * 14 * 4;

  // Error codes matching mpegh_jni.cc.
  private static final int MPEGH_DECODER_ERROR_INVALID_DATA = -1;
  private static final int MPEGH_DECODER_ERROR_OTHER = -2;

  private static final int MPEGH_DECODER_CODEC_MHA1 = 0;
  private static final int MPEGH_DECODER_CODEC_MHM1 = 1;

  private final String codecName;
  private final byte[] extraData;
  private final @C.Encoding int encoding;
  private final int outputBufferSize;
  private final boolean isOutputFloat;

  private long nativeContext;
  private boolean hasOutputFormat;
  private volatile int channelCount;
  private volatile int sampleRate;
  private ByteBuffer buffer;

  public MpeghDecoder(int numInputBuffers,
                      int numOutputBuffers,
                      int initialInputBufferSize,
                      String mimeType,
                      List<byte[]> initializationData,
                      boolean isOutputFloat)
      throws MpeghDecoderException {
    super(new DecoderInputBuffer[numInputBuffers], new SimpleOutputBuffer[numOutputBuffers]);
    if (!MpeghLibrary.isAvailable()) {
      throw new MpeghDecoderException("Failed to load decoder native libraries.");
    }
    this.isOutputFloat = isOutputFloat;
    codecName = MpeghLibrary.getCodecName(mimeType);
    extraData = getExtraData(mimeType, initializationData);
    if (isOutputFloat) {
      encoding = C.ENCODING_PCM_FLOAT;
    } else {
      encoding = C.ENCODING_PCM_16BIT;
    }
    outputBufferSize = OUTPUT_BUFFER_SIZE_SINGLE_PRECISION_FLOAT;

    Log.v(TAG, "MpeghInitialize: ");

    // initialize a decoder
    nativeContext = MpeghInitialize(
        mimeType == MimeTypes.AUDIO_MPEGH_MHA1 ? MPEGH_DECODER_CODEC_MHA1 : MPEGH_DECODER_CODEC_MHM1,
        extraData);

    if (nativeContext == 0) {
      throw new MpeghDecoderException("Initialization failed.");
    }
    setInitialInputBufferSize(initialInputBufferSize);
  }

  @Override
  public String getName() {
    return "mpegh" + MpeghLibrary.getVersion() + "-" + codecName;
  }

  @Override
  protected DecoderInputBuffer createInputBuffer() {
    return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
  }

  @Override
  protected SimpleOutputBuffer createOutputBuffer() {
    return new SimpleOutputBuffer(this::releaseOutputBuffer);
  }

  @Override
  protected MpeghDecoderException createUnexpectedDecodeException(Throwable error) {
    return new MpeghDecoderException("Unexpected decode error", error);
  }

  @Override
  protected MpeghDecoderException decode(
      DecoderInputBuffer inputBuffer, SimpleOutputBuffer outputBuffer, boolean reset) {
    ByteBuffer outputData = outputBuffer.init(inputBuffer.timeUs, outputBufferSize);
    if (isOutputFloat) {
      buffer = outputData;
    } else {
      if (buffer == null || buffer.capacity() < outputBufferSize) {
        buffer = ByteBuffer.allocateDirect(outputBufferSize).order(ByteOrder.nativeOrder());
      }
      buffer.limit(outputBufferSize);
      buffer.position(0);
    }

    if (reset) {
      nativeContext = MpeghReset(nativeContext);
      if (nativeContext == 0) {
        return new MpeghDecoderException("Error resetting (see logcat).");
      }
    }
    if (!hasOutputFormat) {
      channelCount = MpeghGetChannelCount(nativeContext);
      sampleRate = MpeghGetSampleRate(nativeContext);
      hasOutputFormat = true;
    }
    if (inputBuffer.isEndOfStream()) {
      outputBuffer.data.position(0);
      outputBuffer.data.limit(0);
      return null;
    }

    ByteBuffer inputData = inputBuffer.data;
    int inputSize = inputData.limit();
    int result = MpeghDecode(nativeContext, inputData, inputSize, buffer, outputBufferSize);
    if (result == MPEGH_DECODER_ERROR_INVALID_DATA) {
      outputBuffer.data.position(0);
      outputBuffer.data.limit(0);
      return null;
    } else if (result < 0) { // includes result == MPEGH_DECODER_ERROR_OTHER
      return new MpeghDecoderException("Error decoding (see logcat).");
    }
    buffer.limit(result);
    buffer.position(0);

    if (!isOutputFloat) {
      boolean ret = convertFromFloatToInt16(buffer, outputBuffer.data);
      if (!ret) {
        return new MpeghDecoderException("Error resetting (see logcat).");
      }
    }
    outputBuffer.data.position(0);
    return null;
  }

  private boolean convertFromFloatToInt16(ByteBuffer inputBuffer, ByteBuffer outputBuffer) {
    if (inputBuffer == null || outputBuffer == null ||
        inputBuffer.remaining() / (Float.BYTES / Short.BYTES) >
            outputBuffer.capacity() - outputBuffer.position()) {
      return false;
    }

    while (inputBuffer.hasRemaining()) {
      float fsample = inputBuffer.getFloat();
      short isample;
      fsample = fsample * 0x8000;
      fsample = Math.round(fsample);
      if (fsample > 0x7999) fsample = 0x7999;
      if (fsample < -0x8000) fsample = -0x8000;
      isample = (short) fsample;
      outputBuffer.putShort(isample);
    }

    outputBuffer.limit(outputBuffer.position());
    return true;
  }

  @Override
  public void release() {
    super.release();
    MpeghRelease(nativeContext);
    nativeContext = 0;
  }

  /**
   * Returns the channel count of output audio. May only be called after {@link #decode(DecoderInputBuffer inputBuffer, SimpleOutputBuffer outputBuffer, boolean reset)}.
   */
  public int getChannelCount() {
    return channelCount;
  }

  /**
   * Returns the sample rate of output audio. May only be called after {@link #decode(DecoderInputBuffer inputBuffer, SimpleOutputBuffer outputBuffer, boolean reset)}.
   */
  public int getSampleRate() {
    return sampleRate;
  }

  /**
   * Returns the encoding of output audio.
   */
  public @C.Encoding int getEncoding() {
    return encoding;
  }

  /**
   * Returns mpegh-compatible codec-specific initialization data ("extra data"), or {@code null} if
   * not required.
   */
  private static byte[] getExtraData(String mimeType, List<byte[]> initializationData) {
    switch (mimeType) {
      case MimeTypes.AUDIO_MPEGH_MHA1:
        return initializationData.get(0);
      default:
        // Other codecs do not require extra data.
        return null;
    }
  }

  private native long MpeghInitialize(int codecType, byte[] extraData);

  private native int MpeghDecode(long context, ByteBuffer inputData, int inputSize,
                                 ByteBuffer outputBuffer, int outputBufferSize);

  private native int MpeghGetChannelCount(long context);

  private native int MpeghGetSampleRate(long context);

  private native long MpeghReset(long context);

  private native void MpeghRelease(long context);

}
