package io.muun.apollo.presentation.ui.new_operation;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.view.DrawerDialogFragment;
import io.muun.apollo.presentation.ui.view.MuunActionDrawer;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.widget.TextView;
import androidx.annotation.NonNull;
import butterknife.BindView;

import javax.annotation.Nullable;

public class TitleAndDescriptionDrawer extends DrawerDialogFragment {

    CharSequence description;

    public TitleAndDescriptionDrawer() {
    }

    public void setDescription(CharSequence description) {
        this.description = description;
    }
    
    @NonNull
    protected MuunActionDrawer createActionDrawer() {
        final MuunDescriptionDrawer muunActionDrawer = new MuunDescriptionDrawer(requireContext());
        muunActionDrawer.setDescription(description);
        return muunActionDrawer;
    }

    public static class MuunDescriptionDrawer extends MuunActionDrawer {

        @BindView(R.id.muun_action_drawer_description)
        TextView descriptionTextView;

        public MuunDescriptionDrawer(Context context) {
            super(context);
        }

        public MuunDescriptionDrawer(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        protected void setUp(Context context, @Nullable AttributeSet attrs) {
            super.setUp(context, attrs);

            descriptionTextView.setMovementMethod(LinkMovementMethod.getInstance());
        }

        public void setDescription(CharSequence description) {
            descriptionTextView.setText(description);
        }

        @Override
        protected int getLayoutResource() {
            return R.layout.drawer_title_and_description;
        }
    }
}
