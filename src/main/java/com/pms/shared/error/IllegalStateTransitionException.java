package com.pms.shared.error;

/**
 * Thrown when a booking is asked to move to a status its current status does not allow
 * (e.g. cancelling a booking that has already CHECKED_OUT).
 *
 * The rules themselves live in BookingStatus — this class only reports a refusal.
 * Handled in GlobalExceptionHandler -> 409 Conflict.
 */
public class IllegalStateTransitionException extends PmsException {

    public IllegalStateTransitionException() {
        super("Illegal state transition");
    }

    /** Names BOTH states so the client knows what actually went wrong, not just that it did. */
    public IllegalStateTransitionException(String from, String to) {
        super("Can not go from state: " + from + " to state: " + to);
    }
}
