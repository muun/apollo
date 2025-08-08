package io.muun.common.api.error;

import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import org.junit.Test;

import static com.google.common.collect.Range.closed;
import static com.google.common.collect.Range.closedOpen;
import static com.google.common.collect.Range.singleton;
import static org.assertj.core.api.Assertions.assertThat;

public class DisjointRangeToClassMapTest {

    @Test
    public void testGetClassAtEmptyMap() {

        final BaseErrorCode.DisjointRangeToClassMap map =
                new BaseErrorCode.DisjointRangeToClassMap();

        assertThat(map.getClassAt(10)).isNull();
    }

    @Test
    public void testGetClassAtClosedAndOpenBoundaries() {

        final BaseErrorCode.DisjointRangeToClassMap map =
                new BaseErrorCode.DisjointRangeToClassMap();

        map.add(closedOpen(1, 10), Integer.class);

        assertThat(map.getClassAt(0)).isNull();
        assertThat(map.getClassAt(1)).isEqualTo(Integer.class);
        assertThat(map.getClassAt(9)).isEqualTo(Integer.class);
        assertThat(map.getClassAt(10)).isNull();
    }

    @Test
    public void testGetClassAtTouchingRanges() {

        final BaseErrorCode.DisjointRangeToClassMap map =
                new BaseErrorCode.DisjointRangeToClassMap();

        map.add(closedOpen(1, 5), Integer.class);
        map.add(closedOpen(5, 10), String.class);

        assertThat(map.getClassAt(4)).isEqualTo(Integer.class);
        assertThat(map.getClassAt(5)).isEqualTo(String.class);
    }

    @Test
    public void testGetIntersectionAtEmptyMap() {

        final BaseErrorCode.DisjointRangeToClassMap map =
                new BaseErrorCode.DisjointRangeToClassMap();

        assertThat(map.getFirstIntersectionWith(closedOpen(1, 5))).isNull();
    }

    @Test
    public void testGetIntersectionWithSingleRange() {

        final BaseErrorCode.DisjointRangeToClassMap map =
                new BaseErrorCode.DisjointRangeToClassMap();

        map.add(closedOpen(1, 5), Integer.class);

        assertThat(map.getFirstIntersectionWith(closed(-3, 0))).isNull();
        assertRangesAreEqual(map.getFirstIntersectionWith(closed(-3, 1)).fst, singleton(1));
        assertRangesAreEqual(map.getFirstIntersectionWith(closed(-3, 3)).fst, closed(1, 3));
        assertRangesAreEqual(map.getFirstIntersectionWith(closed(-3, 8)).fst, closed(1, 4));
        assertRangesAreEqual(map.getFirstIntersectionWith(closed(1, 3)).fst, closed(1, 3));
        assertRangesAreEqual(map.getFirstIntersectionWith(closed(3, 4)).fst, closed(3, 4));
        assertRangesAreEqual(map.getFirstIntersectionWith(closed(3, 8)).fst, closed(3, 4));
        assertRangesAreEqual(map.getFirstIntersectionWith(closed(4, 8)).fst, singleton(4));
        assertThat(map.getFirstIntersectionWith(closed(5, 8))).isNull();
    }

    @Test
    public void testGetIntersectionWithMultipleRanges() {

        final BaseErrorCode.DisjointRangeToClassMap map =
                new BaseErrorCode.DisjointRangeToClassMap();

        map.add(closedOpen(1, 3), Integer.class);
        map.add(closedOpen(5, 7), String.class);
        map.add(closedOpen(7, 9), Boolean.class);

        assertRangesAreEqual(map.getFirstIntersectionWith(closed(-3, 1)).fst, singleton(1));
        assertRangesAreEqual(map.getFirstIntersectionWith(closed(-3, 6)).fst, closed(1, 2));
        assertRangesAreEqual(map.getFirstIntersectionWith(closed(-3, 10)).fst, closed(1, 2));
        assertRangesAreEqual(map.getFirstIntersectionWith(closed(2, 8)).fst, singleton(2));
        assertRangesAreEqual(map.getFirstIntersectionWith(closed(3, 10)).fst, closed(5, 6));
        assertRangesAreEqual(map.getFirstIntersectionWith(closed(6, 10)).fst, singleton(6));
        assertRangesAreEqual(map.getFirstIntersectionWith(closed(7, 10)).fst, closed(7, 8));
    }

    public void assertRangesAreEqual(Range<Integer> a, Range<Integer> b) {

        if (a.isEmpty()) {
            assertThat(b.isEmpty()).isTrue();
            return;
        }

        final Range<Integer> aCanon = a.canonical(DiscreteDomain.integers());
        final Range<Integer> bCanon = b.canonical(DiscreteDomain.integers());

        assertThat(aCanon).isEqualTo(bCanon);
    }
}