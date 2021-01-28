package io.muun.apollo.domain.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;

public class UriBuilder {

    private String scheme;
    private String host;
    private String path;
    private List<NameValuePair> queryValues = new LinkedList<>();

    public UriBuilder setScheme(String scheme) {
        this.scheme = scheme;
        return this;
    }

    public UriBuilder setHost(String host) {
        this.host = host;
        return this;
    }

    public UriBuilder setPath(String path) {
        this.path = path.startsWith("/") ? path.substring(1) : path;
        return this;
    }

    public UriBuilder addParam(String name, String value) {
        queryValues.add(new NameValuePair(name, value));
        return this;
    }

    /**
     * Convert this Builder to a URI string.
     */
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

        if (queryValues.size() > 0) {
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
