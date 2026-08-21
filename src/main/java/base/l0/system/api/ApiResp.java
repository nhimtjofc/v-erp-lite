package base.l0.system.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** Uniform response envelope. Every REST endpoint returns this shape. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResp<T>(
        Instant timestamp,
        Boolean success,
        ErrorDetail error,
        T content
) {
    public static <T> ApiResp<T> fromData(T data) {
        return new ApiResp<>(Instant.now(), true, null, data);
    }

    public static <T> ApiResp<List<T>> fromList(List<T> data) {
        return new ApiResp<>(Instant.now(), true, null, data);
    }

    public static ApiResp<Object> fromError(ErrorDetail error) {
        return new ApiResp<>(Instant.now(), false, error, null);
    }
}

