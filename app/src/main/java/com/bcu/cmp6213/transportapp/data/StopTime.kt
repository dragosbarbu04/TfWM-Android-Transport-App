package com.bcu.cmp6213.transportapp.data

/**
 * Represents a single scheduled stop event from the GTFS (General Transit Feed Specification)
 * `stop_times.txt` file. Each record in this file describes the arrival and departure
 * of a vehicle at a specific stop for a specific trip.
 *
 * This class is fundamental for constructing timetables and understanding the sequence of stops
 * for any given trip.
 *
 * Note: This data class is not currently Parcelable. If instances of StopTime need to be passed
 * between Android components (e.g., in Intent extras or Bundles), it would need to
 * implement the Parcelable interface (e.g., using `@Parcelize`).
 *
 * Key fields from `stop_times.txt`:
 * - `trip_id`: ID of the trip this stop time belongs to. (Required in GTFS)
 * - `arrival_time`: Arrival time at the stop. (Format: HH:MM:SS, can be > 24:00:00 for overnight trips). (Conditionally Required in GTFS*)
 * - `departure_time`: Departure time from the stop. (Format: HH:MM:SS, can be > 24:00:00). (Conditionally Required in GTFS*)
 * - `stop_id`: ID of the stop. (Required in GTFS)
 * - `stop_sequence`: Order of this stop in the trip sequence. Must be a non-negative integer, and values must increase along the trip. (Required in GTFS)
 * - `stop_headsign`: Text that appears on a sign on the vehicle to identify the stop's destination to passengers. Overrides `trip_headsign` if provided. (Optional)
 * - `pickup_type`: Indicates whether passengers can board at this stop for this trip. (Optional, 0=Regularly scheduled, 1=No pickup, 2=Must phone agency, 3=Must coordinate with driver)
 * - `drop_off_type`: Indicates whether passengers can alight at this stop for this trip. (Optional, 0=Regularly scheduled, 1=No drop off, 2=Must phone agency, 3=Must coordinate with driver)
 * - `shape_dist_traveled`: Actual distance traveled along the shape from the first shape point to this stop. Used to interpolate stop positions. (Optional)
 *
 * *`arrival_time` and `departure_time` are required for time-based stops. For non-time-based stops (e.g., frequency-based services without exact schedules),
 * they can be omitted if both `pickup_type` and `drop_off_type` are 1 (Not available). They must be provided for the first and last stop of a trip.
 */
data class StopTime(
    /**
     * Identifier for the trip to which this stop time event belongs.
     * This links this record to a specific entry in `trips.txt`.
     */
    val tripId: String,

    /**
     * Arrival time at the stop.
     * Format is "HH:MM:SS" (H: Hour, M: Minute, S: Second).
     * Times can be greater than 24:00:00 for services that span past midnight
     * (e.g., 25:30:00 for 1:30 AM the next day).
     * Stored as a String; parsing to a time object is done when specific time calculations are needed.
     * Nullable because it can be omitted for the first stop of a trip if it's the same as `departure_time`,
     * or if the stop is non-time-based.
     */
    val arrivalTime: String?,

    /**
     * Departure time from the stop.
     * Format is "HH:MM:SS", and can also be greater than 24:00:00.
     * Stored as a String.
     * Nullable because it can be omitted for the last stop of a trip if it's the same as `arrivalTime`,
     * or if the stop is non-time-based.
     */
    val departureTime: String?,

    /**
     * Identifier of the stop where this event occurs.
     * This links this record to a specific entry in `stops.txt`.
     */
    val stopId: String,

    /**
     * The order of this stop in the sequence of stops for this trip.
     * Values must be non-negative integers and strictly increasing along the trip.
     * Essential for determining the path and order of stops.
     */
    val stopSequence: Int,

    /**
     * Optional headsign for this stop on this trip. This text is typically displayed
     * on the vehicle and indicates the trip's ultimate destination or direction from this stop.
     * If provided, it overrides the `trip_headsign` from `trips.txt` for this portion of the trip.
     */
    val stopHeadsign: String?

    // Other optional fields from stop_times.txt like `pickup_type`, `drop_off_type`,
    // `shape_dist_traveled`, `timepoint` etc., can be added here if needed
    // for more advanced features (e.g., real-time predictions, exact stop placement on shapes).
    // For example:
    // val pickupType: Int?,
    // val dropOffType: Int?,
    // val shapeDistTraveled: Double?
)