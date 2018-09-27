package io.muun.apollo.domain.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

public class UriParser {

    private String scheme = "";
    private String host = "";
    private String path = "";
    private List<NameValuePair> queryValues = new LinkedList<>();

    /**
     * Construct a parser from a URI.
     */
    public UriParser(String uri) {
        try {
            checkedParse(uri);

        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public String getScheme() {
        return scheme;
    }

    public String getHost() {
        return host;
    }

    public String getPath() {
        return path;
    }

    /**
     * Get the first value for a given query parameter name.
     */
    public String getParam(String name) {
        // For now, we ignore multiple instances of the same parameter (which is otherwise valid).
        // Also, this is O(n). If it ever becomes important, switch to HashMap<LinkedList>, though
        // order will be lost.

        for (NameValuePair pair : queryValues) {
            if (pair.name.equals(name)) {
                return pair.value;
            }
        }

        return null;
    }

    public List<NameValuePair> getParams() {
        return queryValues;
    }

    private void checkedParse(String uriString) throws URISyntaxException {
        // Parsing a URI in pure Java is harder than it should be. Most stdlib methods either
        // fail partially or are tailored to HTTP URIs. A custom implementation I wrote failed in
        // some cases too. I found this compromise:
        final URI uri = new URI(ensureDoubleSlash(uriString));

        scheme = emptyIfNull(uri.getScheme());
        host = emptyIfNull(uri.getHost());
        path = emptyIfNull(uri.getPath());

        if (! path.isEmpty()) {
            path = path.substring(1); // remove leading slash
        }

        final String query = uri.getQuery();

        if (query != null) {
            final String[] pairs = query.split("&");

            for (String pair : pairs) {
                final int eqIndex = pair.indexOf("=");

                final String name = eqIndex > 0
                        ? pair.substring(0, eqIndex)
                        : pair;

                final String value = eqIndex > 0 && pair.length() > eqIndex + 1
                        ? pair.substring(eqIndex + 1)
                        : "";

                queryValues.add(new NameValuePair(name, value));
            }
        }

    }

    private String ensureDoubleSlash(String uriString) throws URISyntaxException {
        final int firstColon = uriString.indexOf(':');

        if (firstColon == -1) {
            throw new URISyntaxException(uriString, "No colon to separate scheme");
        }

        final String beforeColon = uriString.substring(0, firstColon);
        final String afterColon = uriString.substring(firstColon + 1);

        if (afterColon.startsWith("//")) {
            return uriString;
        } else {
            return beforeColon + "://" + afterColon;
        }
    }

    private String emptyIfNull(String string) {
        return (string != null) ? string : "";
    }
}
