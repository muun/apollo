package io.muun.apollo.presentation.ui.listener;


public interface OnBackPressedListener {
    /**
     * Invoked when the BACK action is triggered.
     * @return true if the event was handled, false if the standard handler should still be called.
     */
    boolean onBackPressed();
}
