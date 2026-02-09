package io.muun.apollo.domain.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;
import javax.annotation.CheckReturnValue;

public class UriBuilder {

    private String scheme;
    private String host;
    private String path;
    private final List<NameValuePair> queryValues = new LinkedList<>();


    /**
     * Set a scheme to this Builder's URI string.
     */
    @CheckReturnValue
    public UriBuilder setScheme(String scheme) {
        this.scheme = scheme;
        return this;
    }


    /**
     * Set a host to this Builder's URI string.
     */
    @CheckReturnValue
    public UriBuilder setHost(String host) {
        this.host = host;
        return this;
    }

    /**
     * Set a path to this Builder's URI string.
     */
    @CheckReturnValue
    public UriBuilder setPath(String path) {
        this.path = path.startsWith("/") ? path.substring(1) : path;
        return this;
    }

    /**
     * Add a query param to this Builder's URI string.
     */
    public UriBuilder addParam(String name, String value) {
        queryValues.add(new NameValuePair(name, value));
        return this;
    }

    /**
     * Convert this Builder to a URI string.
     */
    @CheckReturnValue
    public String build() {
        try {
            return checkedBuild();

        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private String checkedBuild() throws UnsupportedEncodingException {
        if (scheme == null || host == null) {
            throw new IllegalArgumentException("URI has no Scheme or Host");
        }

        final StringBuilder sb = new StringBuilder();

        sb.append(scheme);
        sb.append(":");
        sb.append(host);

        if (path != null) {
            sb.append("/");
            sb.append(path);
        }

        if (!queryValues.isEmpty()) {
            sb.append("?");

            for (NameValuePair queryValue : queryValues) {
                sb.append(queryValue.name);
                sb.append("=");
                sb.append(encode(queryValue.value));
                sb.append("&");
            }

            sb.setLength(sb.length() - 1); // remove dangling ampersand
        }

        return sb.toString();
    }

    private String encode(String original) throws UnsupportedEncodingException {
        return URLEncoder.encode(original, "UTF-8");
    }
}
