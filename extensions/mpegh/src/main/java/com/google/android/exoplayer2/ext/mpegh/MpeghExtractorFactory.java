package com.google.android.exoplayer2.ext.mpegh;

/**
 * Created by andrija.milovanovic on 2/5/19.
 */

import android.net.Uri;

import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;

import java.util.List;
import java.util.Map;

public final class MpeghExtractorFactory implements ExtractorsFactory {

    private DefaultExtractorsFactory defaultExtractorsFactory = new DefaultExtractorsFactory();


    @Override
    public Extractor[] createExtractors(Uri uri, Map<String, List<String>> responseHeaders) {
        return createExtractors();
    }
    @Override
    public Extractor[] createExtractors() {
        // create all default extractors
        Extractor[] defaultExtractors = defaultExtractorsFactory.createExtractors();
        Extractor[] mpeghExtractors = MpeghExtractor.FACTORY.createExtractors();

        Extractor[] combinedExtractors = new Extractor[defaultExtractors.length + mpeghExtractors.length];

        System.arraycopy(mpeghExtractors, 0, combinedExtractors, 0, mpeghExtractors.length);
        System.arraycopy(defaultExtractors, 0, combinedExtractors, mpeghExtractors.length, defaultExtractors.length);
        return combinedExtractors;
    }
}