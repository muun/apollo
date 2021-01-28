package io.muun.apollo.presentation.ui.view;

import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import rx.functions.Action2;
import rx.functions.Func2;
import rx.functions.Func3;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public class ViewProps<T extends View> {
    // Well, damn. You found this file. Oh man. This class requires an explanation.
    //
    // When implementing a custom View, you'll want to transfer XML attributes from the root layout
    // (the custom class) to its children. For example, if you have a custom TextView that comes
    // wrapped in a LinearLayout, the `android:textColor` property should cascade down to the inner
    // TextView.
    //
    // A ViewProps instance is a configurable object that allows transferring attributes from an
    // AttributeSet into a View, programmatically and efficiently, while minimizing the explosion of
    // verbosity and bug potential this normally entails.
    //
    // Why so dramatic? Glad you asked.
    //
    // Intuitively, you would want to take the AttributeSet object you get when a LayoutInflater
    // creates your View (the outer LinearLayout in our example), extract some of the attributes,
    // and pass those down to child views selectively.
    //
    // Well, yes, but more importantly no. The AttributeSet object is immutable, so you cannot
    // modify the one you got. Create a new one? There's no implementation of the AttributeSet
    // interface you can use. Implement it in your own class? Hardly, since it MUST be an instance
    // of XmlPullParser as well (the rendering engine explicitly makes this cast, throwing
    // ClassCastException faster than you can say "polymorphism").
    //
    // So you must keep the AttributeSet you were given, read it, and use View instance methods
    // (such as `setTextColor`) to configure your views programmatically. Problem solved, right?
    // Wrong.
    //
    // Styles, which exist in the Context, must be mixed in with the attributes explicitly given to
    // the View. There's also the issues of unit conversion (eg dp-to-px), and reference resolution
    // (eg "@color/background" to ColorStateList, or "@string/text" to String).
    //
    // The answer is TypedArray, as used to create custom attribute namespaces, but using the
    // android.R.attr values instead. This class will solve all the problems mentioned above, but is
    // VERY prone to misuse. You can easily create it wrong or call the incorrect method with the
    // correct signature, and the effects are very hard to debug, since most of the errors will
    // type-check just fine and run without exceptions (although producing garbage).
    //
    // So, ViewProps. This ungodly monster will protect you from the worse monsters, the
    // ones hiding under the lid of the SDK.

    private interface Transfer<T> {
        void apply(T target, TypedArray source, int index);
    }

    private final int[] attrIds;
    private final Transfer<T>[] transfers;

    private ViewProps(int[] attrIds, Transfer<T>[] transfers) {
        this.attrIds = attrIds;
        this.transfers = transfers;
    }

    void transfer(@Nullable AttributeSet attrs, @Nullable T target) {
        if (attrs == null || target == null) {
            return;
        }

        final TypedArray source = target.getContext().obtainStyledAttributes(attrs, attrIds);

        for (int i = 0; i < transfers.length; i++) {
            transfers[i].apply(target, source, i);
        }

        source.recycle(); // MUST be called after use, or GC will fail.
    }

    public static class Builder<T extends View> {

        private final List<Integer> attrIdList = new LinkedList<>();
        private final Map<Integer, Transfer<T>> transferMap = new HashMap<>();

        public Builder<T> addString(int attrId, Action2<T, String> setter) {
            return addProp(attrId, setter, TypedArray::getString);
        }

        public Builder<T> addInt(int attrId, Action2<T, Integer> setter) {
            return addProp(attrId, setter, TypedArray::getInt, 0);
        }

        public Builder<T> addFloat(int attrId, Action2<T, Float> setter) {
            return addProp(attrId, setter, TypedArray::getFloat, 0f);
        }

        public Builder<T> addBoolean(int attrId, Action2<T, Boolean> setter) {
            return addProp(attrId, setter, TypedArray::getBoolean, false);
        }

        public Builder<T> addEnum(int attrId, Action2<T, Integer> setter) {
            return addProp(attrId, setter, TypedArray::getInt, 0);
        }

        public Builder<T> addColor(int attrId, Action2<T, Integer> setter) {
            return addProp(attrId, setter, TypedArray::getColor, 0);
        }

        public Builder<T> addColorList(int attrId, Action2<T, ColorStateList> setter) {
            return addProp(attrId, setter, TypedArray::getColorStateList);
        }

        public Builder<T> addSize(int attrId, Action2<T, Integer> setter) {
            return addProp(attrId, setter, TypedArray::getDimensionPixelSize, 0);
        }

        public Builder<T> addDimension(int attrId, Action2<T, Integer> setter) {
            return addProp(attrId, setter, TypedArray::getLayoutDimension,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }

        public Builder<T> addRef(int attrId, Action2<T, Integer> setter) {
            return addProp(attrId, setter, TypedArray::getResourceId, 0);
        }

        private <U> Builder<T> addProp(int attrId,
                                       Action2<T, U> setter,
                                       Func2<TypedArray, Integer, U> getter) {

            final Transfer<T> transfer = (T target, TypedArray source, int index) -> {
                if (source.hasValue(index)) {
                    setter.call(target, getter.call(source, index));
                }
            };

            return addProp(attrId, transfer);
        }

        private <U> Builder<T> addProp(int attrId,
                                       Action2<T, U> setter,
                                       Func3<TypedArray, Integer, U, U> getter,
                                       U defaultValue) {

            final Transfer<T> transfer = (T target, TypedArray source, int index) -> {
                if (source.hasValue(index)) {
                    setter.call(target, getter.call(source, index, defaultValue));
                }
            };

            return addProp(attrId, transfer);
        }

        private Builder<T> addProp(int attrId, Transfer<T> transfer) {
            attrIdList.add(attrId);
            transferMap.put(attrId, transfer);
            return this;
        }

        ViewProps<T> build() {
            final int[] attrIds = toPrimitiveArray(attrIdList);
            Arrays.sort(attrIds); // TypedArray expects this (undocumented but trust me)

            final Transfer[] transfers = new Transfer[attrIds.length];

            for (int i = 0; i < attrIds.length; i++) {
                transfers[i] = transferMap.get(attrIds[i]);
            }

            return new ViewProps<T>(attrIds, transfers);
        }

        private int[] toPrimitiveArray(List<Integer> list) {
            final int[] array = new int[list.size()];
            int index = 0;

            for (Integer item: list) {
                array[index++] = item;
            }

            return array;
        }
    }
}
