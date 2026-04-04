package dev.cerios.maugame.websocket.interceptor.result;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class Result<T, E extends Exception> {

    private final T value;
    private final E exception;

    private Result(T value, E exception) {
        this.value = value;
        this.exception = exception;
    }

    public static <T1, E1 extends Exception> Result<T1, E1> of(T1 value) {
        return new Result<>(value, null);
    }

    public static <T1, E1 extends Exception> Result<T1, E1> ofError(E1 exception) {
        return new Result<>(null, exception);
    }

    @SuppressWarnings("unchecked")
    public <T1> Result<T1, E> map(Function<? super T, ? extends T1> valueMapper) {
        if (this.value != null) {
            var resolvedValue = valueMapper.apply(this.value);
            if (resolvedValue == null) {
                return (Result<T1, E>) Result.ofError(
                    new ResultException("Value mapped from %s to null.".formatted(this.value)));
            }
            return new Result<>(resolvedValue, this.exception);
        }
        return (Result<T1, E>) this;
    }

    public Result<T, E> or(Supplier<Optional<T>> optValueSupplier) {
        if (this.value != null) {
            return this;
        }
        var optValue = optValueSupplier.get();
        return optValue.<Result<T, E>>map(Result::of).orElse(this);
    }

    public T getOrThrow() throws E {
        if (this.value != null) {
            return this.value;
        }
        throw exception;
    }

    public boolean isSuccessful() {
        return this.value != null;
    }
}
