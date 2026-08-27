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

    // 409, not 400: the request is well-formed and would have been valid at another moment —
    // it is the booking's CURRENT STATE that refuses it. 400 means "I can't process your input".
    @ExceptionHandler(IllegalStateTransitionException.class)
    public ProblemDetail handleInvalidTrasition(IllegalStateTransitionException ex){
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Invalid Status Transition");
        problem.setType(URI.create("https://pms.com/errors/invalid-transition"));
        problem.setProperty("timeStamp", Instant.now());
        return problem;
    }

    // B. Database Constraint Errors.
    // EVERY database rule arrives as this ONE type, so we read the constraint NAME to tell them
    // apart. Trade-off: couples Java to DB identifiers — rename one and this quietly falls back
    // to the generic message.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {

        // getMostSpecificCause() = the last "Caused by:" — Postgres puts the constraint name there.
        String rootMessage = ex.getMostSpecificCause().getMessage();
        String cause = rootMessage == null ? "" : rootMessage.toLowerCase();

        // Never leak raw SQL to the client — but always log it for ourselves.
        logger.error("DB constraint violation", ex);

        HttpStatus status = HttpStatus.CONFLICT;
        String title;
        String detail;
        String type;

        if (cause.contains("no_double_booking")) {
            // ⭐ The centrepiece rule. Reaching here means the app-level availability check and
            // the pessimistic room lock were both bypassed or lost a race — and the DATABASE
            // caught it anyway. That is exactly what the constraint is for.
            title  = "Room Not available";
            detail = "Rooms Not available for the selected dates";
            type   = "https://pms.com/errors/not-available";

        } else if (cause.contains("uq_room_per_hotel")) {
            title  = "Duplicate Room Number";
            detail = "A room with that number already exists in this hotel.";
            type   = "https://pms.com/errors/duplicate";

        // Two names each: schema.sql says inline UNIQUE (-> "hotels_email_key") but the live DB
        // has a named "unique_email". Known drift; ddl-auto=validate does not check constraint names.
        } else if (cause.contains("unique_email") || cause.contains("hotels_email_key")) {
            title  = "Duplicate Email";
            detail = "A hotel with that email address already exists.";
            type   = "https://pms.com/errors/duplicate";

        } else if (cause.contains("unique_phone") || cause.contains("hotels_phone_key")) {
            title  = "Duplicate Phone";
            detail = "A hotel with that phone number already exists.";
            type   = "https://pms.com/errors/duplicate";

        } else if (cause.contains("chk_booking_dates")) {
            // A CHECK failing is bad INPUT, not a conflict. Bean Validation should have caught it.
            status = HttpStatus.BAD_REQUEST;
            title  = "Invalid Date Range";
            detail = "Check-out date must be after the check-in date.";
            type   = "https://pms.com/errors/bad-request";

        } else {
            title  = "Data Conflict";
            detail = "A database constraint was violated. Please check your input.";
            type   = "https://pms.com/errors/data-conflict";
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(type));
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