package io.muun.apollo.data.db.base;

import io.muun.apollo.domain.errors.ErrorClassification;
import io.muun.apollo.domain.errors.MuunError;

import android.text.TextUtils;
import com.squareup.sqldelight.Query;
import org.jetbrains.annotations.NotNull;

public class ElementNotFoundException extends MuunError {

    @NotNull
    @Override
    public ErrorClassification getClassification() {
        return ErrorClassification.UNEXPECTED;
    }

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
