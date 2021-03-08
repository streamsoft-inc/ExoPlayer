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

import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.util.LibraryLoader;
import com.google.android.exoplayer2.util.MimeTypes;

//--------------------------------------------------------------------//
// IA decoder impl
//--------------------------------------------------------------------//

/**
 * Configures and queries the underlying native library.
 */
public final class MpeghLibrary {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.mpegh");
  }

  private static final LibraryLoader LOADER =
      new LibraryLoader("mpegh");

  private MpeghLibrary() {}

  /**
   * Override the names of the mpegh native libraries. If an application wishes to call this
   * method, it must do so before calling any other method defined by this class, and before
   * instantiating a {@link MpeghAudioRenderer} instance.
   *
   * @param libraries The names of the mpegh native libraries.
   */
  public static void setLibraries(String... libraries) {
    LOADER.setLibraries(libraries);
  }

  /**
   * Returns whether the underlying library is available, loading it if necessary.
   */
  public static boolean isAvailable() {
    return LOADER.isAvailable();
  }

  /**
   * Returns the version of the underlying library if available, or null otherwise.
   */
  public static String getVersion() {
    LOADER.isAvailable();
    return MpeghGetVersion();
  }

  /**
   * Returns whether the underlying library supports the specified MIME type.
   *
   * @param mimeType The MIME type to check.
   */
  public static boolean supportsFormat(String mimeType) {
    if (!isAvailable()) {
      return false;
    }
    String codecName = getCodecName(mimeType);
    return codecName != null;
  }

  /**
   * Returns the name of the mpegh decoder that could be used to decode {@code mimeType}.
   */
  /* package */
  static String getCodecName(String mimeType) {
    switch (mimeType) {
      case (MimeTypes.BASE_TYPE_AUDIO + "/mha1") :
        return "mpegh3d.mha1";
      case (MimeTypes.BASE_TYPE_AUDIO + "/mhm1") :
        return "mpegh3d.mhm1";
      default:
        return null;
    }
  }

  private static native String MpeghGetVersion();
}
