package io.muun.common.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Collections {

    private Collections() {
        throw new AssertionError();
    }

    /**
     * Compute the intersection between two collections.
     *
     * <p>Make sure {@code T} implements equals and hashCode.
     */
    public static <T> Set<T> intersection(Collection<? extends T> a, Collection<? extends T> b) {

        final Set<T> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return intersection;
    }

    /**
     * Compute the difference between two collections.
     *
     * <p>Make sure {@code T} implements equals and hashCode.
     */
    public static <T> Set<T> difference(Collection<? extends T> a, Collection<? extends T> b) {

        // You might think we're allocating a wrapper set for nothing here BUT removeAll is
        // accidentally quadratic if the 2nd collection is a) bigger b) can't run contains() in O(1)

        final Set<T> difference = new HashSet<>(a);
        difference.removeAll(new HashSet<>(b));
        return difference;
    }

    /**
     * Compute the union between two collections.
     *
     * <p>Make sure {@code T} implements equals and hashCode.
     */
    @SafeVarargs
    public static <T> Set<T> union(Collection<? extends T> a, Collection<? extends T> ...others) {

        final Set<T> union = new HashSet<>(a);

        for (Collection<? extends T> other : others) {
            union.addAll(other);
        }

        return union;
    }

    /**
     * Compute the symmetric difference between two collections.
     *
     * <p>Make sure {@code T} implements equals and hashCode.
     */
    public static <T> Set<T> symmetricDifference(Collection<? extends T> a,
                                                 Collection<? extends T> b) {

        return union(difference(a, b), difference(b, a));
    }

    /**
     * Determine whether two collections contain exactly the same elements, regardless of the order.
     *
     * <p>Make sure {@code T} implements equals and hashCode.
     */
    public static <T> boolean isEqual(Collection<? extends T> a, Collection<? extends T> b) {

        return symmetricDifference(a, b).isEmpty();
    }

    /**
     * Concatenate two collections.
     */
    @SafeVarargs
    public static <T> List<T> concat(Collection<? extends T> a, Collection<? extends T> ...others) {

        final List<T> concatenation = new ArrayList<>(a);

        for (Collection<? extends T> other : others) {
            concatenation.addAll(other);
        }

        return concatenation;
    }
}
