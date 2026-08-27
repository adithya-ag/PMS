package com.pms.hotel.booking;

import java.util.EnumSet;

/**
 * The booking lifecycle AND the rules for moving through it, in one place.
 *
 *   PENDING ──► CONFIRMED ──► CHECKED_IN ──► CHECKED_OUT
 *      │            │
 *      └──► CANCELLED ◄┘                (CHECKED_OUT and CANCELLED are terminal)
 *
 * Chosen over the classic State pattern (one class per state): these states differ only in
 * WHAT MAY COME NEXT, which is data — not in behaviour. Data belongs in a table, not a class
 * hierarchy. See docs/concurrency-and-locking-notes.md -> Section 15.
 *
 * NOTE: PENDING is currently unreachable — createBooking sets CONFIRMED directly. It was
 * designed for "created, awaiting payment", and payment was cut from scope on 2026-08-19.
 */
public enum BookingStatus {

    PENDING,
    CONFIRMED,
    CHECKED_IN,
    CHECKED_OUT,
    CANCELLED;

    /**
     * ⚠️ Why a switch and NOT constructor arguments like PENDING(EnumSet.of(CONFIRMED, ...)):
     * that version COMPILES but dies at startup with "BookingStatus not an enum". EnumSet is
     * backed by a bitmask built from the enum's constant array — and during the enum's own
     * initialisation that array does not exist yet. A method body runs afterwards, so it is safe.
     *
     * No `default` branch ON PURPOSE: an exhaustive switch over an enum makes the COMPILER
     * force you to handle a new constant. Adding `default` would silently swallow it.
     *
     * (Allocates a small EnumSet per call. A static EnumMap built once would avoid that — not
     * worth the extra code at this scale.)
     */
    public EnumSet<BookingStatus> allowedNext() {
        return switch (this) {
            case PENDING     -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED   -> EnumSet.of(CHECKED_IN, CANCELLED);
            case CHECKED_IN  -> EnumSet.of(CHECKED_OUT);
            case CHECKED_OUT -> EnumSet.noneOf(BookingStatus.class);   // terminal
            case CANCELLED   -> EnumSet.noneOf(BookingStatus.class);   // terminal
        };
    }

    /**
     * The single question every status change must ask.
     * EnumSet, not HashSet: it is a bitmask (one long, one bit per constant), so contains() is
     * a single bit test rather than a hash lookup.
     */
    public boolean canTransitionTo(BookingStatus target) {
        return allowedNext().contains(target);
    }
}
