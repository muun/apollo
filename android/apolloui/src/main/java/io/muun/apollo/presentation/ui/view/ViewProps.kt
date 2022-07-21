package io.muun.apollo.presentation.ui.view

import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import rx.functions.Action2
import rx.functions.Func2
import rx.functions.Func3
import java.util.*

class ViewProps<T : View> private constructor(
    private val attrIds: IntArray,
    private val transfers: Array<Transfer<T>>,
) {
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
        fun apply(target: T, source: TypedArray, index: Int)
    }

    fun transfer(attrs: AttributeSet?, target: T?) {
        if (attrs == null || target == null) {
            return
        }

        val source = target.context.obtainStyledAttributes(attrs, attrIds)
        for (i in transfers.indices) {
            transfers[i].apply(target, source, i)
        }

        source.recycle() // MUST be called after use, or GC will fail.
    }

    class Builder<T : View> {
        private val attrIdList: MutableList<Int> = LinkedList()
        private val transferMap: MutableMap<Int, Transfer<T>> = HashMap()

        @Suppress("RedundantSamConstructor") // They are not redundant here ;)
        fun addString(attrId: Int, setter: (T, String) -> Unit): Builder<T> {
            return addProp(
                attrId,
                Action2<T, String> { obj: T, text: String ->
                    setter(obj, text)
                },
                Func2<TypedArray, Int, String> { obj: TypedArray, index: Int ->
                    obj.getString(index)
                }
            )
        }

        // This special naming is necessary to avoid ambiguous reference in some Java custom views.
        // TODO: remove once all references are migrated to Kotlin
        fun addStringJava(attrId: Int, setter: Action2<T, String>): Builder<T> {
            return addProp(
                attrId,
                setter,
                Func2<TypedArray, Int, String> { obj: TypedArray, index: Int ->
                    obj.getString(index)
                }
            )
        }

        fun addInt(attrId: Int, setter: (T, Int) -> Unit): Builder<T> {
            return addProp(
                attrId,
                { obj: T, int: Int ->
                    setter(obj, int)
                },
                { obj: TypedArray, index: Int, defValue: Int ->
                    obj.getInt(index, defValue)
                },
                0
            )
        }

        // TODO: remove once all custom views are migrated to Kotlin
        fun addInt(attrId: Int, setter: Action2<T, Int>): Builder<T> {
            return addProp(
                attrId,
                setter,
                { obj: TypedArray, index: Int, defValue: Int ->
                    obj.getInt(index, defValue)
                },
                0
            )
        }

        fun addFloat(attrId: Int, setter: (T, Float) -> Unit): Builder<T> {
            return addProp(
                attrId,
                { obj: T, float: Float ->
                    setter(obj, float)
                },
                { obj: TypedArray, index: Int, defValue: Float ->
                    obj.getFloat(index, defValue)
                },
                0f
            )
        }

        // TODO: remove once all custom views are migrated to Kotlin
        fun addFloat(attrId: Int, setter: Action2<T, Float>): Builder<T> {
            return addProp(
                attrId,
                setter,
                { obj: TypedArray, index: Int, defValue: Float ->
                    obj.getFloat(index, defValue)
                },
                0f
            )
        }

        fun addBoolean(attrId: Int, setter: (T, Boolean) -> Unit): Builder<T> {
            return addProp(
                attrId,
                { obj: T, boolean: Boolean ->
                    setter(obj, boolean)
                },
                { obj: TypedArray, index: Int, defValue: Boolean ->
                    obj.getBoolean(index, defValue)
                },
                false
            )
        }

        // TODO: remove once all custom views are migrated to Kotlin
        fun addBoolean(attrId: Int, setter: Action2<T, Boolean>): Builder<T> {
            return addProp(
                attrId,
                setter,
                { obj: TypedArray, index: Int, defValue: Boolean ->
                    obj.getBoolean(index, defValue)
                },
                false
            )
        }

        fun addEnum(attrId: Int, setter: (T, Int) -> Unit): Builder<T> {
            return addProp(
                attrId,
                { obj: T, enumValue: Int ->
                    setter(obj, enumValue)
                },
                { obj: TypedArray, index: Int, defValue: Int ->
                    obj.getInt(index, defValue)
                },
                0
            )
        }

        // TODO: remove once all custom views are migrated to Kotlin
        fun addEnum(attrId: Int, setter: Action2<T, Int>): Builder<T> {
            return addProp(
                attrId,
                setter,
                { obj: TypedArray, index: Int, defValue: Int ->
                    obj.getInt(index, defValue)
                },
                0
            )
        }

        fun addColor(attrId: Int, setter: (T, Int) -> Unit): Builder<T> {
            return addProp(
                attrId,
                { obj: T, color: Int ->
                    setter(obj, color)
                },
                { obj: TypedArray, index: Int, defValue: Int ->
                    obj.getColor(index, defValue)
                },
                0
            )
        }

        // TODO: remove once all custom views are migrated to Kotlin
        fun addColor(attrId: Int, setter: Action2<T, Int>): Builder<T> {
            return addProp(
                attrId,
                setter,
                { obj: TypedArray, index: Int, defValue: Int ->
                    obj.getColor(index, defValue)
                },
                0
            )
        }

        fun addColorList(attrId: Int, setter: (T, ColorStateList) -> Unit): Builder<T> {
            return addProp(
                attrId,
                { obj: T, colorStateList: ColorStateList ->
                    setter(obj, colorStateList)
                },
                { obj: TypedArray, index: Int ->
                    obj.getColorStateList(index)!!
                })
        }

        // TODO: remove once all custom views are migrated to Kotlin
        fun addColorList(attrId: Int, setter: Action2<T, ColorStateList>): Builder<T> {
            return addProp(attrId, setter) { obj: TypedArray, index: Int ->
                obj.getColorStateList(index)!!
            }
        }

        fun addSize(attrId: Int, setter: (T, Int) -> Unit): Builder<T> {
            return addProp(
                attrId,
                { obj: T, size: Int ->
                    setter(obj, size)
                },
                { obj: TypedArray, index: Int, defValue: Int ->
                    obj.getDimensionPixelSize(index, defValue)
                },
                0
            )
        }

        // This special naming is necessary to avoid ambiguous reference in some Java custom views.
        // TODO: remove once all references are migrated to Kotlin
        fun addSizeJava(attrId: Int, setter: Action2<T, Int>): Builder<T> {
            return addProp(
                attrId,
                setter,
                { obj: TypedArray, index: Int, defValue: Int ->
                    obj.getDimensionPixelSize(index, defValue)
                },
                0
            )
        }

        fun addDimension(attrId: Int, setter: (T, Int) -> Unit): Builder<T> {
            return addProp(
                attrId,
                { obj: T, dimen: Int ->
                    setter(obj, dimen)
                },
                { obj: TypedArray, index: Int, defValue: Int ->
                    obj.getLayoutDimension(index, defValue)
                },
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // TODO: remove once all custom views are migrated to Kotlin
        fun addDimension(attrId: Int, setter: Action2<T, Int>): Builder<T> {
            return addProp(
                attrId,
                setter,
                { obj: TypedArray, index: Int, defValue: Int ->
                    obj.getLayoutDimension(index, defValue)
                },
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        fun addRef(attrId: Int, setter: (T, Int) -> Unit): Builder<T> {
            return addProp(
                attrId,
                { obj: T, refId: Int ->
                    setter(obj, refId)
                },
                { obj: TypedArray, index: Int, defValue: Int ->
                    obj.getResourceId(index, defValue)
                },
                0
            )
        }

        // This special naming is necessary to avoid ambiguous reference in some Java custom views.
        // TODO: remove once all references are migrated to Kotlin
        fun addRefJava(attrId: Int, setter: Action2<T, Int>): Builder<T> {
            return addProp(
                attrId,
                setter,
                { obj: TypedArray, index: Int, defValue: Int ->
                    obj.getResourceId(index, defValue)
                },
                0
            )
        }

        private fun <U> addProp(
            attrId: Int,
            setter: Action2<T, U>,
            getter: Func2<TypedArray, Int, U>,
        ): Builder<T> {

            val transfer: Transfer<T> = object : Transfer<T> {
                override fun apply(target: T, source: TypedArray, index: Int) {
                    if (source.hasValue(index)) {
                        setter.call(target, getter.call(source, index))
                    }
                }
            }

            return addProp(attrId, transfer)
        }

        private fun <U> addProp(
            attrId: Int,
            setter: Action2<T, U>,
            getter: Func3<TypedArray, Int, U, U>,
            defaultValue: U,
        ): Builder<T> {

            val transfer: Transfer<T> = object : Transfer<T> {
                override fun apply(target: T, source: TypedArray, index: Int) {
                    if (source.hasValue(index)) {
                        setter.call(target, getter.call(source, index, defaultValue))
                    }
                }
            }

            return addProp(attrId, transfer)
        }

        private fun addProp(attrId: Int, transfer: Transfer<T>): Builder<T> {
            attrIdList.add(attrId)
            transferMap[attrId] = transfer
            return this
        }

        fun build(): ViewProps<T> {
            val attrIds = toPrimitiveArray(attrIdList)
            Arrays.sort(attrIds) // TypedArray expects this (undocumented but trust me)

            val transfers = ArrayList<Transfer<T>>()


            attrIds.forEach { attrId ->
                transfers.add(transferMap[attrId]!!)
            }

            return ViewProps(attrIds, transfers.toTypedArray()) // Not Android's TypedArray!
        }

        private fun toPrimitiveArray(list: List<Int>): IntArray {
            val array = IntArray(list.size)
            var index = 0
            for (item in list) {
                array[index++] = item
            }
            return array
        }
    }
}