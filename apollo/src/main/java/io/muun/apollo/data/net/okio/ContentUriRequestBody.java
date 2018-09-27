package io.muun.apollo.data.net.okio;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.internal.Util;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Request body that takes an android uri and reads its content.
 */
public class ContentUriRequestBody extends RequestBody {

    private final Context context;

    private final MediaType mediaType;

    private final Uri contentUri;

    /**
     * Creates a ContentUriRequestBody.
     */
    public ContentUriRequestBody(Context context, MediaType mediaType, Uri contentUri) {

        this.context = context;
        this.mediaType = mediaType;
        this.contentUri = contentUri;
    }

    @Override
    public MediaType contentType() {
        return mediaType;
    }

    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {

        final InputStream inputStream;

        try {
            inputStream = context.getContentResolver().openInputStream(contentUri);
        } catch (FileNotFoundException e) {
            throw new IOException(e);
        }

        if (inputStream == null) {
            throw new IOException();
        }

        Source source = null;

        try {
            source = Okio.source(inputStream);
            sink.writeAll(source);
        } finally {
            Util.closeQuietly(source);
        }
    }
}
