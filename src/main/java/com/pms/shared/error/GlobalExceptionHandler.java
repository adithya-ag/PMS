package com.pms.shared.error;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // ==========================================
    // 1. FREE HANDLERS (Inherited from Parent)
    // ==========================================
    // You do NOT need to write code for these. 
    // Just by extending ResponseEntityExceptionHandler, these become ProblemDetail automatically:
    // - MethodArgumentNotValidException (Validation errors)
    // - HttpMessageNotReadableException (Bad JSON)
    // - MethodArgumentTypeMismatchException (Type errors in path/query)
    // - HttpRequestMethodNotSupportedException (Wrong HTTP Method)
    
    // Optional: If you want to CUSTOMIZE the validation error format, 
    // you override this method. Otherwise, delete this block to use Spring's default.
    /*
    @Override
    protected ProblemDetail handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, 
            HttpHeaders headers, 
            HttpStatusCode status, 
            WebRequest request) {
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Validation failed");
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://pms.com/errors/validation-error"));
        problem.setProperty("timestamp", Instant.now());
        // Add field errors to properties if needed
        return problem;
    }
    */

    // ==========================================
    // 2. MANUAL HANDLERS (You MUST write these)
    // ==========================================

    // A. Your Custom Business Exceptions
    //
    // NOTE: one handler per exception type. Each new PmsException subclass you add
    // (RoomUnavailableException, DuplicateResourceException, ...) needs its own
    // @ExceptionHandler here — otherwise it falls through to the generic Exception
    // handler below and surfaces as a 500 instead of its intended status.
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://pms.com/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ProblemDetail handleInvalidDateRange(InvalidDateRangeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Date Range");
        problem.setType(URI.create("https://pms.com/errors/bad-request"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(BookingConflictException.class)
    public ProblemDetail handelBookingConflict(BookingConflictException ex){
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Room Not available");
        problem.setType(URI.create("https://pms.com/errors/not-available"));
        problem.setProperty("timeStamp", Instant.now());
        return problem;
    }

    // B. Database Constraint Errors (Unique Email, Foreign Key, etc.)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        // Safety: Don't leak raw SQL errors to the client
        String safeMessage = "A database constraint was violated. Please check your input.";
        
        // Optional: Log the real error internally for debugging
        logger.error("DB constraint violation", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, safeMessage);
        problem.setTitle("Data Conflict");
        problem.setType(URI.create("https://pms.com/errors/data-conflict"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // C. Generic Safety Net (Catches everything else)
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        // NEVER return ex.getMessage() here in production (security risk)
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, 
            "An unexpected internal error occurred."
        );
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://pms.com/errors/internal-error"));
        problem.setProperty("timestamp", Instant.now());
        
        // Log the full stack trace so you can debug it
        logger.error("Unexpected error", ex);
        
        return problem;
    }
}   