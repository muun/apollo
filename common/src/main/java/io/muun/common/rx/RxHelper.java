package io.muun.common.rx;

import rx.Observable;
import rx.functions.Func2;
import rx.functions.Func3;
import rx.functions.Func4;

import java.util.ArrayList;
import java.util.List;

public class RxHelper {

    public static <T1, T2, R> Observable<R> flatZip(
            Observable<? extends T1> o1,
            Observable<? extends T2> o2,
            final Func2<? super T1, ? super T2, Observable<? extends R>> zipFunction) {

        return Observable.merge(Observable.zip(o1, o2, zipFunction));
    }

    public static <T1, T2, T3, R> Observable<R> flatZip(
            Observable<? extends T1> o1,
            Observable<? extends T2> o2,
            Observable<? extends T3> o3,
            Func3<? super T1, ? super T2, ? super T3, Observable<? extends R>> zipFunction) {

        return Observable.merge(Observable.zip(o1, o2, o3, zipFunction));
    }

    public static <T1, T2, T3, T4, R> Observable<R> flatZip(
            Observable<? extends T1> o1,
            Observable<? extends T2> o2,
            Observable<? extends T3> o3,
            Observable<? extends T4> o4,
            Func4<? super T1, ? super T2, ? super T3, ? super T4, Observable<? extends R>>
                    zipFunction) {
        return Observable.merge(Observable.zip(o1, o2, o3, o4, zipFunction));
    }

    public static <T> T identity(T item) {
        return item;
    }

    public static <T1> void nop(T1 item1) {
    }

    public static <T1, T2> void nop(T1 item1, T2 item2) {
    }

    public static <T1, T2, T3> void nop(T1 item1, T2 item2, T3 item3) {
    }

    public static <T1, T2, T3, T4> void nop(T1 item1, T2 item2, T3 item3, T4 item4) {
    }

    public static <T1, T2, T3, T4, T5> void nop(T1 item1, T2 item2, T3 item3, T4 item4, T5 item5) {
    }

    public static <T> Void toVoid(T whatever) {
        return null;
    }

    public static <T1, T2> Void toVoid(T1 ignored1, T2 ignored2) {
        return null;
    }

    public static <T1, T2, T3> Void toVoid(T1 ignored1, T2 ignored2, T3 ignored3) {
        return null;
    }

    public static <T1, T2, T3, T4> Void toVoid(T1 ignored1, T2 ignored2, T3 ignored3, T4 ignored4) {
        return null;
    }

    public static <T1, T2, T3, T4, T5> Void toVoid(T1 ignored1, T2 ignored2, T3 ignored3,
                                                   T4 ignored4, T5 ignored5) {
        return null;
    }

    public static <T1, T2, T3, T4, T5, T6> Void toVoid(T1 ignored1, T2 ignored2, T3 ignored3,
                                                       T4 ignored4, T5 ignored5, T6 ignored6) {
        return null;
    }

    public static <T1, T2, T3, T4, T5, T6, T7> Void toVoid(T1 ignored1, T2 ignored2, T3 ignored3,
                                                           T4 ignored4, T5 ignored5, T6 ignored6,
                                                           T7 ignored7) {
        return null;
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> Void toVoid(T1 ignored1, T2 ignored2,
                                                               T3 ignored3, T4 ignored4,
                                                               T5 ignored5, T6 ignored6,
                                                               T7 ignored7, T8 ignored8) {
        return null;
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> Void toVoid(T1 ignored1, T2 ignored2,
                                                                   T3 ignored3, T4 ignored4,
                                                                   T5 ignored5, T6 ignored6,
                                                                   T7 ignored7, T8 ignored8,
                                                                   T9 ignored9) {
        return null;
    }

    public static <T> T first(T first) {
        return first;
    }

    public static <T1, T2> T1 first(T1 first, T2 ignored2) {
        return first;
    }

    public static <T1, T2, T3> T1 first(T1 first, T2 ignored2, T3 ignored3) {
        return first;
    }

    public static <T1, T2, T3, T4> T1 first(T1 first, T2 ignored2, T3 ignored3, T4 ignored4) {
        return first;
    }

    /**
     * Count the number of `true` values among vararg booleans.
     */
    public static int countTrue(Boolean... bools) {
        int count = 0;

        for (Boolean bool: bools) {
            if (bool) {
                count++;
            }
        }

        return count;
    }

    /**
     * Return a List without null items.
     */
    @SafeVarargs // Tell Java that we dont cast the varargs generics.
    // See https://stackoverflow.com/a/14252221/469300
    public static <T> List<T> toListWithoutNulls(T... items) {
        final List<T> list = new ArrayList<>(items.length);

        for (T item: items) {
            if (item != null) {
                list.add(item);
            }
        }

        return list;
    }
}