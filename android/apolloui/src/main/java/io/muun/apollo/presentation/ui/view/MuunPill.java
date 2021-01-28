package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import butterknife.BindView;

import javax.annotation.Nullable;

public class MuunPill extends MuunView {

    static final ViewProps<MuunPill> viewProps = new ViewProps.Builder<MuunPill>()
            .addString(android.R.attr.text, MuunPill::setText)
            .build();

    @BindView(R.id.muun_pill_picture)
    ProfilePictureView picture;

    @BindView(R.id.muun_pill_text)
    TextView text;

    public MuunPill(Context context) {
        super(context);
    }

    public MuunPill(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunPill(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.muun_pill;
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);

        if (attrs != null) {
            viewProps.transfer(attrs, this);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        post(this::adjustPictureSize);
    }

    public void setText(CharSequence content) {
        text.setText(content);
    }

    public void setPictureUri(@Nullable String pictureUri) {
        picture.setPictureUri(pictureUri);
    }

    public void setPictureUri(@Nullable Uri pictureUri) {
        picture.setPictureUri(pictureUri);
    }

    public void setPictureVisible(boolean isVisible) {
        picture.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    private void adjustPictureSize() {
        final int size = getMeasuredHeight();

        picture.getLayoutParams().width = size;
        picture.getLayoutParams().height = size;
    }
}
