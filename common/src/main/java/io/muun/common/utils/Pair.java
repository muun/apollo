package io.muun.common.utils;

import java.util.Objects;

// clone of com.sun.tools.javac.util.Pair;

public class Pair<A, B> {
    public final A fst;
    public final B snd;

    public Pair(A first, B second) {
        this.fst = first;
        this.snd = second;
    }

    public String toString() {
        return "Pair[" + this.fst + "," + this.snd + "]";
    }

    /**
     * Equals.
     */
    public boolean equals(Object var1) {
        return var1 instanceof Pair
                && Objects.equals(this.fst, ((Pair) var1).fst)
                && Objects.equals(this.snd, ((Pair) var1).snd);
    }

    /**
     * Hashcode.
     */
    public int hashCode() {
        if (this.fst == null) {
            return this.snd == null ? 0 : this.snd.hashCode() + 1;

        } else {
            return this.snd == null
                    ? this.fst.hashCode() + 2
                    : this.fst.hashCode() * 17 + this.snd.hashCode();
        }
    }

    public static <A, B> Pair<A, B> of(A first, B second) {
        return new Pair<>(first, second);
    }
}
