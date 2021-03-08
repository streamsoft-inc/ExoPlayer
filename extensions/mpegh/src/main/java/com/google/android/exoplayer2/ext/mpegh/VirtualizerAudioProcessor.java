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
package com.google.android.exoplayer2.ext.mpegh;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.util.LibraryLoader;
import com.google.android.exoplayer2.util.Log;

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

  static class LocalBuffer {
    private static int SIA_VIR_FRAME_MIDDLE = 0;
    private static int SIA_VIR_FRAME_START = 1;
    private static int SIA_VIR_FRAME_END = 2;

    public int validSample;
    public int streamStatus;
    public ByteBuffer buffer;
    public ByteBuffer processedBuffer;
    public int[] zero_snd_flags;

    private AudioFormat audioFormat;
    private boolean isFirstSample = true;
    private boolean isLastData = false;

    LocalBuffer() {
      buffer = ByteBuffer.allocateDirect(INPUT_BUFFER_SIZE_PER_FRAME).order(ByteOrder.nativeOrder());
      processedBuffer = ByteBuffer.allocateDirect(OUTPUT_BUFFER_SIZE_PER_FRAME).order(ByteOrder.nativeOrder());
    }

    void setFirstTime() {
      isFirstSample = true;
    }

    void setLastData() {
      isLastData = true;
    }

    void setAudioFormat(AudioFormat audioFormat) {
      if (null != this.audioFormat
          && this.audioFormat.sampleRate == audioFormat.sampleRate
          && this.audioFormat.channelCount == audioFormat.channelCount
          && this.audioFormat.encoding == audioFormat.encoding) {
        // Nothig to do.
      } else {
        this.audioFormat = audioFormat;
        zero_snd_flags = new int[audioFormat.channelCount];
        clear();
      }
    }

    void clear() {
      buffer.clear();
      processedBuffer.clear();
      for (int i = 0; i < audioFormat.channelCount; ++i) {
        zero_snd_flags[i] = 0;
      }
    }

    /**
     * In the case of Gapless operation, it is necessary to input to Proc in units of 1024 samples,
     * and in the case of Gapless operation, if it is less than 1024 samples, it is necessary
     * to interpolate with the sample of the next track.
     *
     * @return Necessity of execution of VirtualizerProc
     */
    private boolean isNeedProc() {
      return isLastData() || isFullData();
    }

    private boolean isLastData() {
      return isLastData;
    }

    private boolean isFullData() {
      return (!buffer.hasRemaining());
    }

    private void appendSampleData(ByteBuffer inputBuffer) {
      int position = inputBuffer.position();
      int limit = inputBuffer.limit();
      if (!inputBuffer.hasRemaining()) {
        return;
      }

      int writeSampleByteSize = inputBuffer.remaining();
      int writableByteSize = buffer.remaining();
      if (writableByteSize < writeSampleByteSize) {
        writeSampleByteSize = writableByteSize;
      }
      inputBuffer.limit(position + writeSampleByteSize);
      buffer.put(inputBuffer);
      inputBuffer.limit(limit);
    }

    void doPrepareSingleVirtualizerProc() {
      validSample = SAMPLES_PER_FLAME;
      streamStatus = SIA_VIR_FRAME_MIDDLE;
      if (isLastData) {
        streamStatus = SIA_VIR_FRAME_END;
        isLastData = false;
        setFirstTime();
        if (!isFullData()) {
          validSample = SAMPLES_PER_FLAME - buffer.remaining() / audioFormat.bytesPerFrame;
          writePadding(buffer);
        }
      } else if (isFirstSample) {
        streamStatus = SIA_VIR_FRAME_START;
        isFirstSample = false;
      }
      buffer.flip();
      processedBuffer.clear();
    }

    private void writePadding(ByteBuffer dest) {
      int end = dest.remaining() / Float.BYTES;
      for (int i = 0; i < end; ++i) {
        dest.putFloat((float) 0.0);
      }
    }
  }

  private static final String TAG = "Virtualizer";
  private static final int INPUT_CHANNEL_COUNT = 14;
  private static final int OUTPUT_CHANNEL_COUNT = 2;
  private static final int SAMPLES_PER_FLAME = 1024;
  private static final int BUFFER_SIZE_PER_FRAME = SAMPLES_PER_FLAME * Float.BYTES;
  private static final int INPUT_BUFFER_SIZE_PER_FRAME = BUFFER_SIZE_PER_FRAME * INPUT_CHANNEL_COUNT;
  private static final int OUTPUT_BUFFER_SIZE_PER_FRAME = BUFFER_SIZE_PER_FRAME * OUTPUT_CHANNEL_COUNT;
  private static final int OUTPUT_BUFFER_SIZE = OUTPUT_BUFFER_SIZE_PER_FRAME * 10; // single-precision float

  private static LibraryLoader LOADER;
  private long nativeHandler;
  private boolean initialized;
  private AudioFormat inputAudioFormat;
  private AudioFormat outputAudioFormat;

  private String appRootPath;
  private String configFilePathHrtf;
  private String configFilePathCp;

  private LocalBuffer localBuffer;
  private ByteBuffer tempFloatBuffer;

  static {
    LOADER = new LibraryLoader("mpegh");
  }

  public static void setLibraries(String... libraries) {
    LOADER.setLibraries(libraries);
  }

  /**
   * Creates a new Virtualizer audio processor.
   */
  public VirtualizerAudioProcessor(String appRootPath) {
    Log.d(TAG, "constructor()");
    if (!isAvailable()) {
      // ToDo : Add Exception
    }
    inputAudioFormat = AudioFormat.NOT_SET;
    outputAudioFormat = AudioFormat.NOT_SET;
    nativeHandler = 0;
    initialized = false;

    this.appRootPath = appRootPath;
    CoefConfigFile configFile = new CoefConfigFile(appRootPath);
    configFilePathHrtf = configFile.getRelativeConfigFilePath(CoefConfigFile.CoefType.Hrtf13);
    configFilePathCp = configFile.getRelativeConfigFilePath(CoefConfigFile.CoefType.Cp);

    localBuffer = new LocalBuffer();
  }

  public static boolean isAvailable() {
    boolean isAvailable = LOADER.isAvailable();
    if (!isAvailable) Log.d(TAG, "isAvailable() == false");
    return isAvailable;
  }

  @Override
  public synchronized AudioFormat configure(AudioFormat inputAudioFormat)
          throws UnhandledAudioFormatException {
    if (canSkipConfigure(inputAudioFormat)) {
      return this.outputAudioFormat;
    }

    Log.d(TAG, "configure() :" + inputAudioFormat.toString());
    if (inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
      throw new UnhandledAudioFormatException(inputAudioFormat);
    }

    this.inputAudioFormat = inputAudioFormat;
    this.outputAudioFormat = onConfigure(inputAudioFormat);
    prepareNative(inputAudioFormat);

    initialized = true;
    return this.outputAudioFormat;
  }

  private boolean canSkipConfigure(AudioFormat inputAudioFormat) {
    if (initialized) {
      return this.inputAudioFormat.sampleRate == inputAudioFormat.sampleRate
              && this.inputAudioFormat.channelCount == inputAudioFormat.channelCount
              && this.inputAudioFormat.encoding == inputAudioFormat.encoding;
    }
    return false;
  }

  private AudioFormat onConfigure(AudioFormat inputAudioFormat) {
    localBuffer.setAudioFormat(inputAudioFormat);
    tempFloatBuffer = ByteBuffer.allocateDirect(OUTPUT_BUFFER_SIZE).order(ByteOrder.nativeOrder());

    return new AudioFormat(this.inputAudioFormat.sampleRate, OUTPUT_CHANNEL_COUNT, this.inputAudioFormat.encoding);
  }

  private void prepareNative(AudioFormat inputAudioFormat)
          throws UnhandledAudioFormatException {
    if (nativeHandler != 0) nativeReset();
    nativeHandler = nativeInitialize();
    if (nativeHandler == 0) {
      throw new UnhandledAudioFormatException(inputAudioFormat);
    }
  }

  private void nativeReset() {
    VirtualizerReset(nativeHandler);
  }

  private long nativeInitialize() {
    long ret = VirtualizerInitialize(appRootPath, configFilePathHrtf, configFilePathCp);
    if (0 == ret) Log.e(TAG, "VirtualizerInitialize() Error");
    return ret;
  }

  @Override
  public boolean isActive() {
    boolean isActive = (nativeHandler != 0);
    if (!isActive) Log.d(TAG, "IaActive() == false");
    return isActive;
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    if (!initialized) {
      Log.e(TAG, "queueInput Error not initialized");
      return;
    }
    if (!haveInputDataThatCanGenerateFrame(inputBuffer)) {
      return;
    }

    nativeProcFrame(inputBuffer);
  }

  private boolean haveInputDataThatCanGenerateFrame(ByteBuffer inputBuffer) {
    localBuffer.setAudioFormat(inputAudioFormat);
    localBuffer.appendSampleData(inputBuffer);
    return localBuffer.isNeedProc();
  }

  private void nativeProcFrame(ByteBuffer inputBuffer) {
    localBuffer.doPrepareSingleVirtualizerProc();
    boolean ret = VirtualizerProc(
        nativeHandler,
        localBuffer.streamStatus,
        localBuffer.validSample,
        localBuffer.processedBuffer,
        localBuffer.buffer,
        localBuffer.zero_snd_flags);
    if (ret == false) {
      Log.e(TAG, "VirtualizerProc() called input_size:" + localBuffer.validSample);
    } else {
      localBuffer.processedBuffer.position(0);
      localBuffer.processedBuffer.limit(OUTPUT_BUFFER_SIZE_PER_FRAME);

      tempFloatBuffer.clear();
      tempFloatBuffer.put(localBuffer.processedBuffer);
    }
    localBuffer.clear();
  }

  /**
   * Mpeg-H Audio playback only (for termination processing of continuous playback of the same Codec of Virtualizer).
   * A different Renderer instance is used for Mpeg-H Audio and another Codec.
   * When switching to a different Codec, the Renderer switches to another instance,
   * and the Renderer for Mpeg-H Audio does not know that the Codec has switched.
   * Call this function when a Codec change occurs to guarantee the playback of the final data.
   * Except for AudioProcessor for Mpeg-h Audio, there is no problem with empty implementation.
   */
  public void endOfCodec() {
    localBuffer.setLastData();
  }

  @Override
  public ByteBuffer getOutput() {
    if (!initialized) {
      Log.e(TAG, "getOutput Error not initialized");
      return EMPTY_BUFFER;
    }

    if (!haveOutputDataThatCanGenerateFrame()) {
      return EMPTY_BUFFER;
    }

    return onGetOutput();
  }

  private boolean haveOutputDataThatCanGenerateFrame() {
    tempFloatBuffer.flip();
    return tempFloatBuffer.hasRemaining();
  }

  private ByteBuffer onGetOutput() {
    int outputByteFloat = tempFloatBuffer.remaining();
    ByteBuffer retBuffer = ByteBuffer.allocateDirect(outputByteFloat).order(ByteOrder.nativeOrder());
    retBuffer.put(tempFloatBuffer);
    retBuffer.flip();
    tempFloatBuffer.clear();

    return retBuffer;
  }

  @Override
  public void queueEndOfStream() {
    if (!initialized) {
      Log.e(TAG, "queueEndOfStream Error not initialized");
      return;
    }
    onQueueEndOfStream();
  }

  /**
   * Called when the end-of-stream is queued to the processor.
   */
  protected void onQueueEndOfStream() {
    // Do nothing.
  }

  @Override
  public boolean isEnded() {
    // [Fix:Gapless]
    // When called from AudioSink#drainAudioProcessorsToEndOfStream(),
    // return true so that buffer output is continued for Gapless playback.
//        if (inputEnded) Log.d(TAG, "isEnded() == true");
//        return inputEnded;
    return true;
  }

  @Override
  public void flush() {
    onFlush();
  }

  private void onFlush() {
    VirtualizerReset(nativeHandler);
    localBuffer.clear();
    localBuffer.setFirstTime();
  }

  @Override
  public synchronized void reset() {
    onReset();

    nativeRelease();
    initialized = false;
    nativeHandler = 0;
  }

  private void onReset() {
    // Nothing to do.
  }

  private void nativeRelease() {
    VirtualizerRelease(nativeHandler);
  }


  private native long VirtualizerInitialize(String rootPath, String configFilePathHrtf, String configFilePathCp);
  private native boolean VirtualizerProc(long context, int status, int validSamples, ByteBuffer outputData, ByteBuffer inputData, int[] zeroSndFlag);
  private native long VirtualizerReset(long context);
  private native void VirtualizerRelease(long context);
}
