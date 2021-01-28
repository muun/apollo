package io.muun.apollo.presentation.ui.new_operation;

public enum NewOperationStep {
    RESOLVING,
    ENTER_AMOUNT,
    ENTER_DESCRIPTION,
    CONFIRM,
    FINISH;

    public NewOperationStep getNext() {
        return (ordinal() < values().length) ? values()[ordinal() + 1] : null;
    }

    public NewOperationStep getPrevious() {
        return (ordinal() > 0) ? values()[ordinal() - 1] : null;
    }
}
