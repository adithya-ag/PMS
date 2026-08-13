package com.pms.hotel.room;

/**
 * Mirrors the Postgres type:
 *   CREATE TYPE room_status AS ENUM ('AVAILABLE', 'OCCUPIED', 'CLEANING', 'MAINTENANCE');
 *
 * Names must match the Postgres labels exactly.
 */
public enum RoomStatus {
    AVAILABLE,
    OCCUPIED,
    CLEANING,
    MAINTENANCE
}
