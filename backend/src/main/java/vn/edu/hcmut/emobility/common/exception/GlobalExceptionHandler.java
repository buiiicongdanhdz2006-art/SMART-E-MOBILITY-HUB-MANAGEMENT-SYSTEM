package vn.edu.hcmut.emobility.common.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts exceptions to a uniform JSON body: {status, message, errors, timestamp}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        return body(e.getStatus(), e.getMessage(), e.getErrors());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException e) {
        return body(400, "Dữ liệu gửi lên sai định dạng", List.of());
    }

    /** Wrong type in a path variable / query parameter, e.g. ?status=FOO. */
    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(TypeMismatchException e) {
        return body(400, "Giá trị không hợp lệ: " + e.getValue(), List.of());
    }

    /** Unique constraint (e.g. two requests registering the same e-mail at the same time). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException e) {
        return body(409, "Dữ liệu bị trùng hoặc vi phạm ràng buộc", List.of());
    }

    /** Another transaction holds the row lock (vehicle / slot / port being booked at the same moment). */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleLock(PessimisticLockingFailureException e) {
        return body(409, "Tài nguyên đang được người khác đặt, vui lòng thử lại", List.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleState(IllegalStateException e) {
        return body(409, e.getMessage(), List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleArgument(IllegalArgumentException e) {
        return body(400, e.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        // Spring MVC errors (404 unknown path, 405 wrong method, 400 missing parameter, 415 ...) keep their status.
        if (e instanceof ErrorResponse er) {
            int status = er.getStatusCode().value();
            return body(status, status == 404 ? "Không tìm thấy đường dẫn" : e.getMessage(), List.of());
        }
        log.error("Unexpected error", e);
        return body(500, "Lỗi hệ thống: " + e.getClass().getSimpleName(), List.of());
    }

    private ResponseEntity<Map<String, Object>> body(int status, String message, List<String> errors) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("message", message);
        m.put("errors", errors);
        m.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(m);
    }
}
