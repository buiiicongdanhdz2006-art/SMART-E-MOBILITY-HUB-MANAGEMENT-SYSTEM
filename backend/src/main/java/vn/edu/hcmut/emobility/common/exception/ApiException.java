package vn.edu.hcmut.emobility.common.exception;

import java.util.List;

/**
 * Business error returned to the client with an HTTP status code and the list of messages
 * (validation errors, "already registered", "no free port", ...).
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final List<String> errors;

    public ApiException(int status, String message, List<String> errors) {
        super(message);
        this.status = status;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public ApiException(int status, String message) {
        this(status, message, List.of());
    }

    public static ApiException badRequest(String message) { return new ApiException(400, message); }
    public static ApiException validation(List<String> errors) { return new ApiException(400, "Dữ liệu không hợp lệ", errors); }
    public static ApiException unauthorized(String message) { return new ApiException(401, message); }
    public static ApiException forbidden(String message) { return new ApiException(403, message); }
    public static ApiException notFound(String what) { return new ApiException(404, "Không tìm thấy " + what); }
    public static ApiException conflict(String message) { return new ApiException(409, message); }
    public static ApiException unavailable(String message) { return new ApiException(503, message); }

    public int getStatus() { return status; }
    public List<String> getErrors() { return errors; }
}
