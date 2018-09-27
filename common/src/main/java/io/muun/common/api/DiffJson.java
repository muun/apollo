package io.muun.common.api;

import io.muun.common.model.Diff;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.Set;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiffJson<T> {

    public static <T> DiffJson<T> fromDiff(Diff<T> diff) {
        return new DiffJson<>(diff.added, diff.removed);
    }

    @NotNull
    public Set<T> added;

    @NotNull
    public Set<T> removed;

    /**
     * Json constructor.
     */
    public DiffJson() {
    }

    /**
     * Manual constructor.
     */
    public DiffJson(Set<T> added, Set<T> removed) {
        this.added = Collections.unmodifiableSet(added);
        this.removed = Collections.unmodifiableSet(removed);
    }

    public Diff<T> toDiff() {
        final Diff<T> diff = new Diff<>();
        diff.added.addAll(added);
        diff.removed.addAll(removed);

        return diff;
    }
}
