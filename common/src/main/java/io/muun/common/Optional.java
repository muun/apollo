package io.muun.common;

import io.muun.common.rx.RxHelper;
import io.muun.common.utils.Preconditions;

import java.util.NoSuchElementException;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public final class Optional<ValueT> {

    private static final Optional<?> EMPTY = new Optional<>();

    @Nullable
    private final ValueT value;

    private Optional() {
        value = null;
    }

    private Optional(ValueT value) {
        this.value = Preconditions.checkNotNull(value);
    }

    @SuppressWarnings("unchecked")
    public static <ValueT> Optional<ValueT> empty() {
        return (Optional<ValueT>) EMPTY;
    }

    public static <ValueT> Optional<ValueT> of(ValueT value) {
        return new Optional<>(value);
    }

    public static <ValueT> Optional<ValueT> ofNullable(ValueT value) {
        return value == null ? Optional.empty() : of(value);
    }

    public static Optional<Double> ifNonNegative(double value) {
        return (value >= 0) ? Optional.of(value) : Optional.empty();
    }

    public boolean isPresent() {
        return value != null;
    }

    /**
     * Returns the value contained by this optional or throws NoSuchElementException.
     */
    @NotNull
    public ValueT get() throws NoSuchElementException {

        if (!isPresent()) {
            throw new NoSuchElementException();
        }

        return value;
    }

    /**
     * If there is a value present executes {@code consumer.consume(get())}.
     */
    public Conditional ifPresent(Consumer<? super ValueT> consumer) {

        if (isPresent()) {
            consumer.consume(get());
            return RxHelper::nop;
        }

        return Action::run;
    }

    public interface Conditional {

        void orElse(Action action);
    }

    /**
     * If there is a value and passes the predicate, this returns an optional with the same value,
     * otherwise return an empty optional.
     */
    public Optional<ValueT> filter(Predicate<? super ValueT> predicate) {

        if (isPresent() && predicate.test(get())) {
            return this;
        }

        return empty();
    }

    /**
     * If there is a value present executes {@code action.consume(get())} and returns an optional
     * with the same value.
     */
    public Optional<ValueT> peek(Consumer<? super ValueT> action) {

        if (isPresent()) {
            action.consume(get());
        }

        return this;
    }

    /**
     * If there is a value present it returns an optional with {@code mapper.map(get())} if that
     * value is non-null, and an empty one otherwise.
     */
    public <U> Optional<U> map(Mapper<? super ValueT, ? extends U> mapper) {

        if (!isPresent()) {
            return empty();
        }

        return ofNullable(mapper.map(get()));
    }

    /**
     * Flat version of map.
     */
    public <U> Optional<U> flatMap(Mapper<? super ValueT, Optional<U>> mapper) {

        if (!isPresent()) {
            return empty();
        }

        return Preconditions.checkNotNull(mapper.map(get()));
    }

    /**
     * If there is a value present this returns an optional with the same value, otherwise return
     * the optional returned by {@code otherOptionalProducer.produce()}.
     */
    public Optional<ValueT> ifEmptyGet(Producer<Optional<ValueT>> otherOptionalProducer) {

        if (isPresent()) {
            return this;
        }

        return Preconditions.checkNotNull(otherOptionalProducer.produce());
    }

    /**
     * Returns a value if present, or {other} otherwise.
     */
    public ValueT orElse(@Nullable ValueT other) {

        if (!isPresent()) {
            return other;
        }

        return get();
    }

    /**
     * Returns a value if present, or {@code otherValueProducer.produce()} otherwise.
     */
    public ValueT orElseGet(Producer<? extends ValueT> otherValueProducer) {

        if (!isPresent()) {
            return otherValueProducer.produce();
        }

        return get();
    }

    /**
     * Returns a value if present, or throws {@code exceptionProducer.produce()} otherwise.
     */
    public <X extends Throwable> ValueT orElseThrow(Producer<? extends X> exceptionProducer)
            throws X {

        if (!isPresent()) {
            throw exceptionProducer.produce();
        }

        return get();
    }

    @Override
    public boolean equals(Object other) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof Optional)) {
            return false;
        }

        final Optional<?> optional = (Optional<?>) other;

        return value == null ? optional.value == null : value.equals(optional.value);
    }

    @Override
    public int hashCode() {

        return value != null ? value.hashCode() : 0;
    }

    @Override
    public String toString() {

        if (!isPresent()) {
            return "Optional.empty";
        }

        return "Optional[" + get() + "]";
    }

    public interface Action {

        void run();
    }

    public interface Producer<ProducedT> {

        ProducedT produce();
    }

    public interface Predicate<TestedT> {

        boolean test(TestedT toTest);
    }

    public interface Consumer<ConsumedT> {

        void consume(ConsumedT toConsume);
    }

    public interface Mapper<SourceT, ResultT> {

        ResultT map(SourceT source);
    }
}
