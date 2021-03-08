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
 * An {@link AudioProcessor} that uses {@code AlcAudioProcessor} to provide rendering of
 * Auto Level Control for playback sound.
 */
public final class AlcAudioProcessor implements AudioProcessor {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.alc");
  }

  static class LocalOutputBufferQueue {
    private final int bufferSize;
    private ByteBuffer tempFloatBuffer;

    LocalOutputBufferQueue(int bufferSize) {
      this.bufferSize = bufferSize;
      tempFloatBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder());
    }

    synchronized void reset() {
      tempFloatBuffer.clear();
    }

    synchronized void enqueue(ByteBuffer input) {
      tempFloatBuffer.put(input);
    }

    synchronized ByteBuffer getOutputBuffer() {
      if (0 == tempFloatBuffer.position()) {
        return EMPTY_BUFFER;
      }

      ByteBuffer retBuffer = tempFloatBuffer;
      retBuffer.flip();

      tempFloatBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder());
      return retBuffer;
    }
  }

  private static final String TAG = "Alc";
  private static final int INPUT_CHANNEL_COUNT = 2;
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

  private LocalOutputBufferQueue localOutputBufferQueue;

  static {
    LOADER = new LibraryLoader("mpeghaudio");
  }

  public static void setLibraries(String... libraries) {
    LOADER.setLibraries(libraries);
  }

  /**
   * Creates a new ALC audio processor.
   */
  public AlcAudioProcessor() {
    Log.d(TAG, "constructor()");
    if (!isAvailable()) {
      // ToDo : Add Exception
    }
    inputAudioFormat = AudioFormat.NOT_SET;
    outputAudioFormat = AudioFormat.NOT_SET;
    nativeHandler = 0;
    initialized = false;
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
    localOutputBufferQueue = new LocalOutputBufferQueue(OUTPUT_BUFFER_SIZE);
    return inputAudioFormat;
  }

  protected void prepareNative(AudioFormat inputAudioFormat)
          throws UnhandledAudioFormatException {
    if (nativeHandler != 0) nativeReset();
    nativeHandler = nativeInitialize();
    if (nativeHandler == 0) {
      throw new UnhandledAudioFormatException(inputAudioFormat);
    }
  }

  private void nativeReset() {
    AlcRelease(nativeHandler);
  }

  private long nativeInitialize() {
    long ret = AlcInitialize();
    if (0 == ret) Log.e(TAG, "AlcInitialize() Error");
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
    return inputBuffer.hasRemaining();
  }

  private void nativeProcFrame(ByteBuffer inputBuffer) {
    int writeSize = inputBuffer.limit();
    boolean ret = AlcProcFrame(nativeHandler, 0, inputBuffer, inputBuffer.limit());
    if (ret) {
      inputBuffer.position(0);
      inputBuffer.limit(writeSize);
      localOutputBufferQueue.enqueue(inputBuffer);
    }
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
    // always true.
    return true;
  }

  private ByteBuffer onGetOutput() {
    return localOutputBufferQueue.getOutputBuffer();
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
  private void onQueueEndOfStream() {
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
    localOutputBufferQueue.reset();
  }

  @Override
  public synchronized void reset() {
    onReset();

    nativeRelease();
    initialized = false;
    nativeHandler = 0;
  }

  private void onReset() {
    onFlush();
  }

  private void nativeRelease() {
    AlcRelease(nativeHandler);
  }


  private native long AlcInitialize();
  private native boolean AlcProcFrame(long context, int offset, ByteBuffer outputData, int outputSize);
  private native void AlcRelease(long context);
}
