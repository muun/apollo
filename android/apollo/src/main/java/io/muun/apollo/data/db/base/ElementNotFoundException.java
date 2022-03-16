package io.muun.apollo.data.db.base;

import io.muun.apollo.domain.errors.MuunError;

import android.text.TextUtils;
import com.squareup.sqldelight.Query;

public class ElementNotFoundException extends MuunError {

    public ElementNotFoundException(Query<?> query) {
        super("Expected unique result for query not found. Statement: " + query.toString());
        getMetadata().put("query", query.toString());
    }

    /**
     * Set arguments in metadata.
     */
    public void setArgs(String... args) {
        getMetadata().put("args", TextUtils.join(",", args));
    }
}
