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

    // B. Database Constraint Errors (Unique Email, Foreign Key, the EXCLUDE constraint, ...)
    //
    // Spring translates Hibernate's ConstraintViolationException into this one type, so EVERY
    // database rule that fires arrives here: a duplicate hotel email, a bad foreign key, and
    // the no_double_booking EXCLUDE constraint all look identical at this point.
    //
    // A single generic 409 for all of them is honest but unhelpful — the client is told "a
    // constraint was violated" and cannot tell whether to change the dates, change the email,
    // or report a bug. So we look at WHICH constraint fired and give a specific message for the
    // handful a user can actually trigger, falling back to the generic message for the rest.
    //
    // Trade-off being made here: this couples Java to database identifiers. Rename a constraint
    // in schema.sql and this silently degrades to the generic message — it won't break, it just
    // gets less helpful. That's the accepted cost; the alternative (parsing SQLSTATEs) tells you
    // the CATEGORY of failure but never WHICH rule, which is the part users need.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {

        // getMostSpecificCause() unwraps the chain down to the driver's own exception —
        // the same instinct as "read the LAST Caused by:" when debugging by hand.
        // Postgres puts the constraint name in that message, e.g.
        //   ERROR: conflicting key value violates exclusion constraint "no_double_booking"
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

        // Two names checked per rule because schema.sql and the live databases have DRIFTED:
        // schema.sql declares an inline `email varchar NOT NULL UNIQUE`, which Postgres would
        // auto-name "hotels_email_key", but the running database actually has an explicitly
        // named "unique_email". Matching both keeps this correct whichever way the drift is
        // resolved. (Resolving it properly is a schema decision — see PROGRESS.md.)
        } else if (cause.contains("unique_email") || cause.contains("hotels_email_key")) {
            title  = "Duplicate Email";
            detail = "A hotel with that email address already exists.";
            type   = "https://pms.com/errors/duplicate";

        } else if (cause.contains("unique_phone") || cause.contains("hotels_phone_key")) {
            title  = "Duplicate Phone";
            detail = "A hotel with that phone number already exists.";
            type   = "https://pms.com/errors/duplicate";

        } else if (cause.contains("chk_booking_dates")) {
            // A CHECK constraint failing is a BAD REQUEST, not a conflict: nothing is competing,
            // the input itself is invalid. Bean Validation should normally catch this first —
            // reaching here means a validation gap worth investigating.
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