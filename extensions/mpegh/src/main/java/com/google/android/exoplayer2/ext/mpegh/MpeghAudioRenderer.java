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
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DecoderAudioRenderer;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;

//--------------------------------------------------------------------//
// IA decoder impl
//--------------------------------------------------------------------//

/** Decodes and renders audio using MPEG-H Music Profile. */
public final class MpeghAudioRenderer extends DecoderAudioRenderer<MpeghDecoder> {

  private static final String TAG = "MpeghAudioRenderer";

  /** The number of input and output buffers. */
  private static final int NUM_BUFFERS = 16;
  /** The default input buffer size. */
  private static final int DEFAULT_INPUT_BUFFER_SIZE = 960 * 6;

  private final boolean enableFloatOutput;

  /**
   * Creates a new instance.
   *
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param appRootPath A file path of application root (Context.getFilesDir().getParent()).
   * @param enableFloatOutput Output encoding setting. ture:32bit float / false:16bit integer
   */
  public MpeghAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      String appRootPath,
      boolean enableFloatOutput) {
    super(
        eventHandler,
        eventListener,
        enableFloatOutput == true
            ? new MpeghAudioSink(new MpeghAudioSink.DefaultAudioProcessorChain(), appRootPath, false, false)
            : new DefaultAudioSink(null, new DefaultAudioSink.DefaultAudioProcessorChain(), false, false, false));
    this.enableFloatOutput = enableFloatOutput;
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  @FormatSupport
  protected int supportsFormatInternal(Format format) {
    String mimeType = Assertions.checkNotNull(format.sampleMimeType);
    if (!MpeghLibrary.isAvailable() || !MimeTypes.isAudio(mimeType)) {
      return FORMAT_UNSUPPORTED_TYPE;
    } else if (!MpeghLibrary.supportsFormat(mimeType)
        || (!sinkSupportsFormat(format, C.ENCODING_PCM_16BIT)
            && !sinkSupportsFormat(format, C.ENCODING_PCM_FLOAT))) {
      return FORMAT_UNSUPPORTED_SUBTYPE;
    } else if (format.exoMediaCryptoType != null) {
      return FORMAT_UNSUPPORTED_DRM;
    } else {
      return FORMAT_HANDLED;
    }
  }

  @Override
  @AdaptiveSupport
  public final int supportsMixedMimeTypeAdaptation() {
    return ADAPTIVE_NOT_SEAMLESS;
  }

  @Override
  protected MpeghDecoder createDecoder(Format format, @Nullable ExoMediaCrypto mediaCrypto)
      throws MpeghDecoderException {
    TraceUtil.beginSection("createMpeghDecoder");
    int initialInputBufferSize =
        DEFAULT_INPUT_BUFFER_SIZE;
    MpeghDecoder decoder =
        new MpeghDecoder(
            NUM_BUFFERS, NUM_BUFFERS, initialInputBufferSize,
            format.sampleMimeType, format.initializationData, enableFloatOutput);
    TraceUtil.endSection();
    return decoder;
  }

  @Override
  public Format getOutputFormat(MpeghDecoder decoder) {
    Assertions.checkNotNull(decoder);
    return new Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setChannelCount(decoder.getChannelCount())
        .setSampleRate(decoder.getSampleRate())
        .setPcmEncoding(decoder.getEncoding())
        .build();
  }

  /**
   * Returns whether the renderer's {@link AudioSink} supports the PCM format that will be output
   * from the decoder for the given input format and requested output encoding.
   */
  private boolean sinkSupportsFormat(Format inputFormat, @C.PcmEncoding int pcmEncoding) {
    return sinkSupportsFormat(
        Util.getPcmFormat(pcmEncoding, inputFormat.channelCount, inputFormat.sampleRate));
  }

}
