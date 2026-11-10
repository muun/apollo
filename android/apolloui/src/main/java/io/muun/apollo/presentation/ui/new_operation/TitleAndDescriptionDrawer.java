package io.muun.apollo.presentation.ui.new_operation;

import io.muun.apollo.databinding.DrawerTitleAndDescriptionBinding;
import io.muun.apollo.presentation.ui.view.DrawerDialogFragment;
import io.muun.apollo.presentation.ui.view.MuunActionDrawer;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function3;

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

        private DrawerTitleAndDescriptionBinding binding;

        public MuunDescriptionDrawer(Context context) {
            super(context);
        }

        public MuunDescriptionDrawer(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public Function3<LayoutInflater, ViewGroup, Boolean, ViewBinding> bindingInflater() {
            return DrawerTitleAndDescriptionBinding::inflate;
        }

        @Override
        protected void setUp(Context context, @Nullable AttributeSet attrs) {
            super.setUp(context, attrs);
            binding = (DrawerTitleAndDescriptionBinding) getBinding();
            binding.muunActionDrawerDescription.setMovementMethod(LinkMovementMethod.getInstance());
        }

        @Override
        @Nullable
        protected TextView getDrawerTitle() {
            return binding != null ? binding.muunActionDrawerTitle : null;
        }

        public void setDescription(CharSequence description) {
            binding.muunActionDrawerDescription.setText(description);
        }
    }
}
