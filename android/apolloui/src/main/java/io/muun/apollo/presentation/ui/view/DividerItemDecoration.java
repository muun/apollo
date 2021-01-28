package io.muun.apollo.presentation.ui.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;

public class DividerItemDecoration extends RecyclerView.ItemDecoration {

    private static final int[] ATTRS = new int[]{
            android.R.attr.listDivider
    };

    private final Drawable divider;
    private final int leftMargin;
    private final int rightMargin;

    /**
     * Create a UI line divider between list items.
     *
     * @param context an {Activity} context.
     * @param leftMargin left margin in dps.
     * @param rightMargin right margin in dps.
     */
    public DividerItemDecoration(Context context, int leftMargin, int rightMargin) {

        final TypedArray a = context.obtainStyledAttributes(ATTRS);
        divider = a.getDrawable(0);
        a.recycle();

        final float scale = context.getResources().getDisplayMetrics().densityDpi / 160f;
        this.leftMargin = (int) (leftMargin * scale);
        this.rightMargin = (int) (rightMargin * scale);
    }

    @Override
    public void onDraw(Canvas canvas, RecyclerView parent) {

        final int left = parent.getPaddingLeft() + leftMargin;
        final int right = parent.getWidth() - parent.getPaddingRight() - rightMargin;

        final int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = parent.getChildAt(i);
            final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child
                    .getLayoutParams();
            final int top = child.getBottom() + params.bottomMargin;
            final int bottom = top + divider.getIntrinsicHeight();
            divider.setBounds(left, top, right, bottom);
            divider.draw(canvas);
        }
    }

    @Override
    public void getItemOffsets(Rect outRect, int itemPosition, RecyclerView parent) {
        outRect.set(0, 0, 0, divider.getIntrinsicHeight());
    }
}
