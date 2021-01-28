package io.muun.apollo.data.db.base;

import io.muun.apollo.domain.errors.MuunError;

import android.text.TextUtils;

public class ElementNotFoundException extends MuunError {

    public ElementNotFoundException(String querySql) {
        super("Expected unique result for query not found. Statement: " + querySql);
        this.getMetadata().put("query", querySql);
    }

    public void setArgs(String... args) {
        this.getMetadata().put("args", TextUtils.join(",", args));
    }
}
