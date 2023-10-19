package io.muun.common.utils;

import io.muun.common.Optional;

import rx.functions.Func1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class CollectionUtils {

    /**
     * Apply a function to a list, returning a new one.
     */
    public static <T, R> List<R> mapList(List<T> original, Func1<T, R> mapper) {
        final List<R> mapped = new ArrayList<>(original.size());

        for (T t : original) {
            mapped.add(mapper.call(t));
        }

        return mapped;
    }

    /**
     * Filters a list, by creating a new one and adding only the items that satisfy a specified
     * predicate.
     */
    public static <T> List<T> filterList(List<T> original, Func1<? super T, Boolean> predicate) {
        final List<T> filteredList = new ArrayList<>();

        for (T t : original) {
            if (predicate.call(t)) {
                filteredList.add(t);
            }
        }

        return filteredList;
    }

    /**
     * Locate the first object in a list that satisfies a specified predicate.
     */
    public static <T> Optional<T> find(List<T> items, Func1<? super T, Boolean> predicate) {
        for (final T item : items) {
            if (predicate.call(item)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    /**
     * Return `true` if any item in a list satisfies a specified predicate.
     */
    public static <T> boolean any(List<T> items, Func1<? super T, Boolean> predicate) {
        return find(items, predicate).isPresent();
    }

    /**
     * Create a SortedMap from a List of Pairs. If keys collide, latest value will be preserved.
     */
    public static <K extends Comparable, V> SortedMap<K, V> mapPairs(List<Pair<K, V>> pairs) {
        final SortedMap<K, V> map = new TreeMap<>(); // preserve order

        for (final Pair<K, V> pair : pairs) {
            map.put(pair.fst, pair.snd);
        }

        return map;
    }

    /**
     * Create a list of pairs by zipping together two lists of elements.
     */
    public static <T, U> List<Pair<T, U>> zip(List<T> a, List<U> b) {
        Preconditions.checkArgument(a.size() == b.size());

        final List<Pair<T, U>> result = new ArrayList<>(a.size());

        for (int i = 0; i < a.size(); i++) {
            final T first = a.get(i);
            final U second = b.get(i);

            result.add(new Pair<>(first, second));
        }

        return result;
    }

    /**
     * Create a map by zipping together two lists of elements.
     */
    public static <K, V> Map<K, V> zipToMap(List<K> keys, List<V> values) {
        Preconditions.checkArgument(keys.size() == values.size());

        final Map<K, V> map = new HashMap<K, V>();
        final Iterator<K> keyIterator = keys.iterator();
        final Iterator<V> valueIterator = values.iterator();

        while (keyIterator.hasNext() && valueIterator.hasNext()) {
            final K key = keyIterator.next();
            final V value = map.put(key, valueIterator.next());
            if (value != null) {
                throw new IllegalArgumentException(
                        "Keys are not unique! Key " + key + " found more then once");
            }
        }

        return map;
    }
}
