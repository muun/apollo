package io.muun.apollo.presentation.ui.view;


import io.muun.apollo.R;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.domain.libwallet.RecoveryCodeV2;
import io.muun.apollo.presentation.ui.utils.StyledStringRes;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import androidx.annotation.NonNull;
import butterknife.BindView;
import butterknife.BindViews;
import rx.functions.Action0;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class MuunRecoveryCodeBox extends MuunView {

    public interface OnEditedListener {
        void onEdited(String content);
    }

    // This is a bit cumbersome, but these views require IDs to have persistent state:
    @BindViews({
            R.id.recovery_code_box_input_1,
            R.id.recovery_code_box_input_2,
            R.id.recovery_code_box_input_3,
            R.id.recovery_code_box_input_4,
            R.id.recovery_code_box_input_5,
            R.id.recovery_code_box_input_6,
            R.id.recovery_code_box_input_7,
            R.id.recovery_code_box_input_8
            })
    List<MuunTextInput> segmentInputs;

    @BindView(R.id.recovery_code_error)
    TextView errorText;

    OnEditedListener editedListener;

    public MuunRecoveryCodeBox(Context context) {
        super(context);
    }

    public MuunRecoveryCodeBox(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunRecoveryCodeBox(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.muun_recovery_code_box;
    }

    @Override
    protected void setUp(@NonNull Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);

        Preconditions.checkState(segmentInputs.size() == RecoveryCodeV2.SEGMENT_COUNT);

        for (int index = 0; index < segmentInputs.size(); index++) {
            final MuunTextInput input = segmentInputs.get(index);

            input.setHintEnabled(false); // remove extra margins
            input.addFilter(new InputFilter.AllCaps());

            input.disableErrorAndHelperTextTempFix(); // TODO replace this with reasonable API

            input.setOnChangeListener(string -> onSegmentInputEdited());
            input.setOnKeyListener(new SegmentAutoReturnKeyListener(index));

            input.editText.addTextChangedListener(new SegmentAutoAdvanceToNextListener(index));
        }
    }

    /**
     * Call requestFocus on the first segment input set to be editable.
     */
    public void requestFocusOnFirstEditableSegment() {
        for (int i = 0; i < segmentInputs.size(); i++) {
            if (isSegmentEditable(i)) {
                segmentInputs.get(i).requestFocusInput();
                break;
            }
        }
    }

    /**
     * Set on soft keyboard next listeners.
     */
    public void setOnKeyboardNextListeners() {
        final List<MuunTextInput> editableSegments = getEditableSegments();

        for (int i = 0; i < editableSegments.size() - 1; i++) {
            final MuunTextInput input = editableSegments.get(i);
            final MuunTextInput nextInput = editableSegments.get(i + 1);

            input.setNextFocusForwardId(nextInput.getId());
            input.setOnKeyboardNextListener(nextInput::requestFocus);
        }

        if (!editableSegments.isEmpty()) {
            final MuunTextInput last = editableSegments.get(editableSegments.size() - 1);
            last.setImeOptions(EditorInfo.IME_ACTION_DONE);
        }
    }

    /**
     * Set on soft keyboard done listener. Probably to do submit an action.
     */
    public void setOnKeyboardDoneListener(MuunTextInput.OnKeyboardDoneListener listener) {
        final List<MuunTextInput> editableSegments = getEditableSegments();

        final MuunTextInput lastEditableSegment = editableSegments.get(editableSegments.size() - 1);
        lastEditableSegment.setOnKeyboardDoneListener(listener);
    }

    /**
     * Set the Box content from a given RecoveryCode.
     */
    public void setRecoveryCode(RecoveryCodeV2 newRecoveryCode) {
        for (int i = 0; i < RecoveryCodeV2.SEGMENT_COUNT; i++) {
            if (!isSegmentEditable(i)) {
                segmentInputs.get(i).setTextSkipListener(newRecoveryCode.getSegment(i));
            }
        }
    }

    /**
     * Set whether all segments are editable.
     */
    public void setEditable(boolean isEditable) {
        for (int i = 0; i < RecoveryCodeV2.SEGMENT_COUNT; i++) {
            setSegmentEditable(i, isEditable);
        }
    }

    /**
     * Set whether a specific RecoveryCode segment is editable.
     */
    public void setSegmentEditable(int index, boolean isEditable) {
        segmentInputs.get(index).setEnabled(isEditable);
    }

    /**
     * Return true if a specific segment is currently editable.
     */
    public boolean isSegmentEditable(int index) {
        return segmentInputs.get(index).isEnabled();
    }

    /**
     * Clear text from a specific segment.
     */
    public void clearSegment(int index) {
        segmentInputs.get(index).setTextSkipListener("");
    }

    /**
     * Set the displayed user-facing error.
     */
    public void setError(@Nullable UserFacingError error) {
        if (error != null) {
            errorText.setText(error.getLocalizedMessage());
            errorText.setVisibility(View.VISIBLE);
        } else {
            errorText.setVisibility(View.GONE);
        }
        errorText.setOnClickListener(null); // Reset legacy onClick handling
    }

    /**
     * Set special, one of a kind, error for RC-only Login flow.
     */
    public void showLegacyRecoveryCodeError(Action0 goToLegacySignIn) {
        errorText.setText(
                new StyledStringRes(getContext(), R.string.rc_only_login_legacy_error)
                        .toCharSequence()
        );
        errorText.setVisibility(View.VISIBLE);
        errorText.setOnClickListener(v -> goToLegacySignIn.call());
    }

    public void setOnEditedListener(OnEditedListener listener) {
        this.editedListener = listener;
    }

    private void onSegmentInputEdited() {
        if (editedListener != null) {
            editedListener.onEdited(getSegmentInputsContent());
        }
    }

    /**
     * Build a (potentially partial or invalid) recovery code string from input state.
     */
    public String getSegmentInputsContent() {
        final StringBuilder sb = new StringBuilder();

        for (MuunTextInput input : segmentInputs) {
            sb.append(input.getText().toString());
            sb.append(RecoveryCodeV2.SEPARATOR);
        }

        sb.setLength(sb.length() - 1); // remove last SEPARATOR

        return sb.toString();
    }

    private List<MuunTextInput> getEditableSegments() {
        final List<MuunTextInput> editableSegments = new ArrayList<>();
        for (int i = 0; i < segmentInputs.size(); i++) {
            if (isSegmentEditable(i)) {
                editableSegments.add(segmentInputs.get(i));
            }
        }
        return editableSegments;
    }

    /**
     * TL;DR: This makes deletions make the cursor jump back, as necessary.
     * This KeyListener handles moving the cursor between segment inputs, to make them behave as if
     * they were a single input. HOWEVER, Android does not reliably reports keyEvents from
     * soft keyboard. Lots of evidence but trust me, even if for some devices this works fine, it
     * is DEFINITELY NOT GUARANTEED to work on all devices. See:
     * - OnKeyListener javaDocs
     * - View#setOnKeyListener javadocs
     * - https://developer.android.com/training/keyboard-input/commands.html
     * - https://developer.android.com/reference/android/view/KeyEvent
     * - Pretty much eveyone in the internes, e.g https://stackoverflow.com/a/52133414/901465
     * So.... we ONLY use this this listener to handle deletions, which is proven to work somewhat
     * reliably and is EXACTLY the weak spot of TextWatcher (our way out to correctly handling
     * moving FORWARD between segment inputs). Yeah, what one listener does not handle, the other
     * listener kinda does. Awesome job guys!
     */
    private class SegmentAutoReturnKeyListener implements OnKeyListener {
        final int ownIndex;

        public SegmentAutoReturnKeyListener(int segmentInputIndex) {
            this.ownIndex = segmentInputIndex;
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            final MuunTextInput nextInput;

            if (isBackspace(event) && (isInputEmpty(ownIndex) || isDeletingAtStart(ownIndex))) {
                nextInput = getNextInput(ownIndex, Order.DESC);

            } else {
                return false; // nothing to do.
            }

            if (nextInput != null) {
                nextInput.requestFocus();

                if (isBeforeEdit(event)) {
                    nextInput.sendKeyEvent(event);
                    return true; // consume this event, we have delegated handling to another input.
                }
            }

            return false; // this event was not consumed by us, let it bubble.
        }

        private boolean isBeforeEdit(KeyEvent event) {
            final int action = event.getAction();
            return (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_MULTIPLE);
        }

        private boolean isBackspace(KeyEvent event) {
            return (event.getKeyCode() == KeyEvent.KEYCODE_DEL);
        }

        private boolean isDeletingAtStart(int inputIndex) {
            return segmentInputs.get(inputIndex).editText.getSelectionStart() == 0;
        }
    }

    /**
     * TL;DR: This makes additions make the cursor jump forward, as necessary.
     * This TextWatcher handles moving the cursor between segment inputs, to make them behave as if
     * they were a single input. HOWEVER, though it can handle and correctly detect deletions, it
     * DOES NOT have any way to detect a delete on an empty input (which is EXACTLY what we need to
     * do to move the cursor backwards to the previous segment with content). There's no event
     * whatsoever in this scenario. So we just use this listener for additions and resort to a
     * KeyListener for deletions.
     */
    private class SegmentAutoAdvanceToNextListener implements TextWatcher {

        private final int ownIndex;

        public SegmentAutoAdvanceToNextListener(int ownIndex) {
            this.ownIndex = ownIndex;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {

            if (s.length() == RecoveryCodeV2.SEGMENT_LENGTH) {
                final MuunTextInput nextInput = getNextInput(ownIndex, Order.ASC);
                if (nextInput != null) {
                    nextInput.requestFocus();
                }
            }
        }
    }

    private boolean isInputFull(int index) {
        return segmentInputs.get(index).getText().length() == RecoveryCodeV2.SEGMENT_LENGTH;
    }

    private boolean isInputEmpty(int index) {
        return segmentInputs.get(index).getText().length() == 0;
    }

    private enum Order {
        DESC,
        ASC
    }

    @Nullable
    private MuunTextInput getNextInput(int segmentIndex, Order order) {
        final int step = order.equals(Order.ASC) ? 1 : -1;
        final int start = segmentIndex + step;

        for (int i = start; i >= 0 && i < segmentInputs.size(); i += step) {
            final MuunTextInput segmentInput = segmentInputs.get(i);

            if (segmentInput.isEnabled()) {
                if (order.equals(Order.ASC) && !isInputFull(i)) {
                    // When moving forward, skip full inputs:
                    return segmentInput;

                } else if (order.equals(Order.DESC) && !isInputEmpty(i)) {
                    // When moving backward, skip empty inputs:
                    return segmentInput;
                }
            }
        }

        return null;
    }
}
