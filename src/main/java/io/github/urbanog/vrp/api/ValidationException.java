package io.github.urbanog.vrp.api;

import java.util.List;

/** A request was well-formed JSON but semantically invalid; mapped to HTTP 422. */
public final class ValidationException extends RuntimeException {
    /** One problem with one field of the request, e.g. {@code instance.customers[3].demand}. */
    public record FieldError(String field, String message) {}

    private final List<FieldError> errors;

    public ValidationException(List<FieldError> errors) {
        super(errors.size() == 1 ? errors.get(0).field() + ": " + errors.get(0).message()
                : errors.size() + " validation errors");
        this.errors = List.copyOf(errors);
    }

    public ValidationException(String field, String message) {
        this(List.of(new FieldError(field, message)));
    }

    public List<FieldError> errors() {
        return errors;
    }
}
