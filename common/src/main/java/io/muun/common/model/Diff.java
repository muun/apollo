package io.muun.common.model;

import java.util.HashSet;
import java.util.Set;

import javax.validation.constraints.NotNull;


public class Diff<T> {

    @NotNull
    public Set<T> added = new HashSet<>();

    @NotNull
    public Set<T> removed = new HashSet<>();

    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty();
    }
}
