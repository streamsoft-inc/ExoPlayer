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
package com.google.android.exoplayer2.ext.mpegh;

import android.os.Handler;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.SimpleDecoderAudioRenderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.util.MimeTypes;

//--------------------------------------------------------------------//
// IA decoder impl
//--------------------------------------------------------------------//

/**
 * Decodes and renders audio using MPEG-H Music Profile.
 */
public final class MpeghAudioRenderer extends SimpleDecoderAudioRenderer {

  /**
   * The number of input and output buffers.
   */
  private static final int NUM_BUFFERS = 16;
  /**
   * The initial input buffer size. Input buffers are reallocated dynamically if this value is
   * insufficient.
   */
  private static final int INITIAL_INPUT_BUFFER_SIZE = 960 * 6; // ToDo : check buffer size

  private MpeghDecoder decoder;
  private static String appRootPath;

  public MpeghAudioRenderer() {
    this(null, null, null);
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param appRootPath A file path of application root (Context.getFilesDir().getAbsolutePath()).
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public MpeghAudioRenderer(Handler eventHandler, AudioRendererEventListener eventListener,
                            String appRootPath, AudioProcessor... audioProcessors) {
    this(eventHandler, eventListener, appRootPath, new DefaultAudioSink(null, audioProcessors));
  }

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param appRootPath A file path of application root (Context.getFilesDir().getAbsolutePath()).
   * @param audioSink The sink to which audio will be output.
   */
  public MpeghAudioRenderer(Handler eventHandler, AudioRendererEventListener eventListener,
                            String appRootPath, AudioSink audioSink) {
    super(
        eventHandler,
        eventListener,
        /* drmSessionManager= */ null,
        /* playClearSamplesWithoutKeys= */ false,
        audioSink);
    this.appRootPath = appRootPath;
  }

  @Override
  protected int supportsFormatInternal(DrmSessionManager<ExoMediaCrypto> drmSessionManager,
      Format format) {
    String sampleMimeType = format.sampleMimeType;
    if (!MpeghLibrary.isAvailable() || !MimeTypes.isAudio(sampleMimeType)) {
      return FORMAT_UNSUPPORTED_TYPE;
    } else if (!MpeghLibrary.supportsFormat(sampleMimeType) || !isOutputSupported(format)) {
      return FORMAT_UNSUPPORTED_SUBTYPE;
    } else if (!supportsFormatDrm(drmSessionManager, format.drmInitData)) {
      return FORMAT_UNSUPPORTED_DRM;
    } else {
      return FORMAT_HANDLED;
    }
  }

  @Override
  public final int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
    return ADAPTIVE_NOT_SEAMLESS;
  }

  @Override
  protected MpeghDecoder createDecoder(Format format, ExoMediaCrypto mediaCrypto)
      throws MpeghDecoderException {
    decoder = new MpeghDecoder(NUM_BUFFERS, NUM_BUFFERS, INITIAL_INPUT_BUFFER_SIZE,
        format.sampleMimeType, appRootPath, format.initializationData);
    return decoder;
  }

  @Override
  public Format getOutputFormat() {
    int channelCount = decoder.getChannelCount();
    int sampleRate = decoder.getSampleRate();
    @C.PcmEncoding int encoding = decoder.getEncoding();
    return Format.createAudioSampleFormat(null, MimeTypes.AUDIO_RAW, null, Format.NO_VALUE,
        Format.NO_VALUE, channelCount, sampleRate, encoding, null, null, 0, null);
  }

  private boolean isOutputSupported(Format inputFormat) {
    return shouldUseFloatOutput(inputFormat);
  }

  private boolean shouldUseFloatOutput(Format inputFormat) {
    if (!supportsOutputEncoding(C.ENCODING_PCM_FLOAT)) {
      return false;
    }
    // For all other formats, assume that it's worth using 32-bit float encoding.
    return true;
  }

}
