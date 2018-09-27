package io.muun.apollo.domain.action.base;

public final class ActionState<T> {

    private static final ActionState EMPTY_STATE = new ActionState<>(Kind.EMPTY, null, null);

    private static final ActionState LOADING_STATE = new ActionState<>(Kind.LOADING, null, null);

    private final Kind kind;

    private final T value;

    private final Throwable throwable;

    @SuppressWarnings("unchecked")
    public static <T> ActionState<T> createEmpty() {
        return EMPTY_STATE;
    }

    @SuppressWarnings("unchecked")
    public static <T> ActionState<T> createLoading() {
        return LOADING_STATE;
    }

    public static <T> ActionState<T> createValue(T value) {
        return new ActionState<>(Kind.VALUE, value, null);
    }

    public static <T> ActionState<T> createError(Throwable throwable) {
        return new ActionState<>(Kind.ERROR, null, throwable);
    }

    public static ActionState<Void> dropValue(ActionState<?> otherState) {
        return new ActionState<>(otherState.kind, null, otherState.throwable);
    }

    private ActionState(Kind kind, T value, Throwable throwable) {
        this.kind = kind;
        this.value = value;
        this.throwable = throwable;
    }

    public boolean isEmpty() {
        return kind == Kind.EMPTY;
    }

    public boolean isLoading() {
        return kind == Kind.LOADING;
    }

    public boolean isValue() {
        return kind == Kind.VALUE;
    }

    public boolean isError() {
        return kind == Kind.ERROR;
    }

    public T getValue() {
        return value;
    }

    public Throwable getError() {
        return throwable;
    }

    public Kind getKind() {
        return kind;
    }

    public enum Kind {
        EMPTY,
        LOADING,
        VALUE,
        ERROR
    }
}
