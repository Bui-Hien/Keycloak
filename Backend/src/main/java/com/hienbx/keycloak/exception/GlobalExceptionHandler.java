package com.hienbx.keycloak.exception;

import com.hienbx.keycloak.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private String translateMessage(String message) {
        if (message == null) {
            return "Đã xảy ra lỗi không xác định.";
        }

        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("incorrect old password")) {
            return "Mật khẩu cũ không chính xác.";
        }
        if (lowerMessage.contains("old password cannot be empty")) {
            return "Mật khẩu cũ không được để trống.";
        }
        if (lowerMessage.contains("new password cannot be empty")) {
            return "Mật khẩu mới không được để trống.";
        }
        if (lowerMessage.contains("username cannot be empty")) {
            return "Tên đăng nhập không được để trống.";
        }
        if (lowerMessage.contains("username already exists")) {
            if (message.contains(": ")) {
                return "Tên đăng nhập đã tồn tại: " + message.substring(message.indexOf(": ") + 2);
            }
            return "Tên đăng nhập đã tồn tại.";
        }
        if (lowerMessage.contains("user not found with id or username")) {
            if (message.contains(": ")) {
                return "Không tìm thấy người dùng với ID hoặc tên đăng nhập: " + message.substring(message.indexOf(": ") + 2);
            }
            return "Không tìm thấy người dùng.";
        }
        if (lowerMessage.contains("failed to delete user in keycloak")) {
            return "Xóa người dùng trên Keycloak thất bại.";
        }
        if (lowerMessage.contains("failed to create user in keycloak")) {
            return "Tạo người dùng trên Keycloak thất bại.";
        }
        if (lowerMessage.contains("user exists with same username") || lowerMessage.contains("user_exists") || lowerMessage.contains("username_exists")) {
            return "Tên đăng nhập đã tồn tại trong hệ thống Keycloak.";
        }
        if (lowerMessage.contains("user exists with same email") || lowerMessage.contains("email_exists")) {
            return "Email đã tồn tại trong hệ thống Keycloak.";
        }

        return message;
    }

    @ExceptionHandler(WebApplicationException.class)
    public ResponseEntity<ErrorResponse> handleWebApplicationException(WebApplicationException ex, HttpServletRequest request) {
        log.error("Keycloak client WebApplicationException: ", ex);
        
        Response response = ex.getResponse();
        int status = HttpStatus.INTERNAL_SERVER_ERROR.value();
        String message = ex.getMessage();
        
        if (response != null) {
            status = response.getStatus();
            try {
                if (response.hasEntity()) {
                    String entity = response.readEntity(String.class);
                    if (entity != null && !entity.trim().isEmpty()) {
                        message = entity;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to read entity from JAX-RS Response", e);
            }
        }

        HttpStatus httpStatus = HttpStatus.resolve(status);
        if (httpStatus == null) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        // Translate the message
        String translatedMsg = translateMessage(message);
        if (translatedMsg.equals(message)) {
            // Apply status-based translation if not translated by content mapping
            if (status == 409) {
                translatedMsg = "Dữ liệu bị trùng lặp hoặc người dùng đã tồn tại trong hệ thống Keycloak.";
            } else if (status == 404) {
                translatedMsg = "Không tìm thấy thông tin người dùng hoặc tài nguyên trên Keycloak.";
            } else if (status == 401) {
                translatedMsg = "Không thể xác thực với Keycloak. Vui lòng kiểm tra lại cấu hình client.";
            } else if (status == 403) {
                translatedMsg = "Không có quyền thực hiện thao tác này trên Keycloak.";
            } else if (status == 400) {
                translatedMsg = "Yêu cầu gửi đến Keycloak không hợp lệ.";
            } else {
                translatedMsg = "Lỗi kết nối hoặc xử lý từ hệ thống Keycloak: " + message;
            }
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .error(httpStatus.getReasonPhrase())
                .message(translatedMsg)
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(httpStatus).body(errorResponse);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        log.error("Access Denied Exception: ", ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .message("Từ chối truy cập: Bạn không có quyền thực hiện thao tác này.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.error("Illegal Argument Exception: ", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(translateMessage(ex.getMessage()))
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.error("Validation Exception: ", ex);
        
        Map<String, String> details = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            
            if (errorMessage != null) {
                if (errorMessage.toLowerCase().contains("must not be empty") || errorMessage.toLowerCase().contains("must not be blank")) {
                    errorMessage = "không được phép để trống.";
                } else if (errorMessage.toLowerCase().contains("must be a well-formed email address")) {
                    errorMessage = "định dạng email không hợp lệ.";
                }
            }
            details.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Dữ liệu gửi lên không hợp lệ.")
                .path(request.getRequestURI())
                .details(details)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        // Handle Tomcat/Spring invalid query parameter format gracefully as 400 Bad Request
        if (ex.getClass().getName().contains("InvalidParameterException") || 
            (ex.getCause() != null && ex.getCause().getClass().getName().contains("InvalidParameterException")) ||
            (ex.getMessage() != null && ex.getMessage().contains("Invalid chunk"))) {
            
            log.warn("Invalid parameter structure from client request: {}", ex.getMessage());
            
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(HttpStatus.BAD_REQUEST.value())
                    .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                    .message("Tham số yêu cầu (URL query parameters) không hợp lệ hoặc sai định dạng.")
                    .path(request.getRequestURI())
                    .build();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        log.error("Unhandled Exception: ", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message(translateMessage(ex.getMessage()))
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
