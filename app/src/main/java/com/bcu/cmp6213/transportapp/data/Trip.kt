package com.bcu.cmp6213.transportapp.data

/**
 * Represents a trip from the GTFS (General Transit Feed Specification) `trips.txt` file.
 * A trip is a specific journey taken by a transit vehicle along a route, serving a sequence
 * of stops at particular times.
 *
 * Each trip is associated with a route, a service schedule (defined in `calendar.txt` or
 * `calendar_dates.txt`), and optionally, a physical path (defined in `shapes.txt`).
 * The sequence of stops and their timings for each trip are detailed in `stop_times.txt`.
 *
 * Note: This data class is not currently Parcelable. If instances of Trip need to be passed
 * between Android components (e.g., in Intent extras or Bundles), it would need to
 * implement the Parcelable interface (e.g., using `@kotlinx.parcelize.Parcelize`).
 *
 * Key fields from `trips.txt`:
 * - `route_id`: Identifier of the route this trip belongs to. (Required in GTFS)
 * - `service_id`: Identifier of the service pattern (e.g., weekday, weekend service) this trip operates under. (Required in GTFS)
 * - `trip_id`: Unique identifier for this specific trip instance. (Required in GTFS)
 * - `trip_headsign`: Text that appears on a sign on the vehicle, identifying the trip's destination to passengers (e.g., "University Campus"). (Optional)
 * - `direction_id`: Indicates the direction of travel for a trip, useful for routes with bi-directional travel. (Optional, typically 0 for one direction, 1 for the other)
 * - `shape_id`: Identifier of the physical path (shape) this trip follows on a map. (Optional, links to `shapes.txt`)
 * - `block_id`: ID for a block of trips that are operated by the same vehicle and an interlined Schedul. (Optional)
 * - `wheelchair_accessible`: Indicates if the trip is wheelchair accessible. (Optional, 0 = No info, 1 = Accessible, 2 = Not accessible)
 * - `bikes_allowed`: Indicates if bicycles are allowed on this trip. (Optional, 0 = No info, 1 = Allowed, 2 = Not allowed)
 */
data class Trip(
    /**
     * The `route_id` of the [GtfsRoute] to which this trip belongs.
     * This links the trip to its general route information (name, type, etc.).
     * Required by GTFS.
     */
    val routeId: String,

    /**
     * The `service_id` indicating the service pattern for this trip.
     * This links the trip to an entry in `calendar.txt` or `calendar_dates.txt`,
     * which defines the days of the week and date range when this service operates.
     * Required by GTFS.
     */
    val serviceId: String,

    /**
     * A unique identifier for this specific trip.
     * This `tripId` is used in `stop_times.txt` to define the schedule of stops for this journey.
     * Required by GTFS.
     */
    val tripId: String,

    /**
     * The headsign that will be displayed on the vehicle for this trip.
     * This often indicates the trip's destination or direction.
     * Example: "City Centre via High Street".
     * This field is optional in GTFS. If null, `stop_headsign` from `stop_times.txt` might be used for specific stops,
     * or it might be inferred from the route name.
     */
    val tripHeadsign: String?,

    /**
     * Indicates the direction of travel for a trip. This field is useful on routes
     * that run in two directions (e.g., outbound and inbound).
     * Typically, "0" represents travel in one direction and "1" in the opposite direction.
     * The meaning of "0" and "1" can vary between agencies.
     * This field is optional in GTFS.
     */
    val directionId: String?, // Typically "0" (e.g., outbound) or "1" (e.g., inbound)

    /**
     * The `shape_id` identifying the physical path that this trip follows on a map.
     * This links the trip to a specific shape defined in `shapes.txt`.
     * If this field is omitted, the trip is not associated with a specific drawn path,
     * and path inference might be based on stop locations.
     * This field is optional in GTFS.
     */
    val shapeId: String?

    // Other optional fields from trips.txt like `block_id`, `wheelchair_accessible`,
    // `bikes_allowed` can be added here if they are relevant to the application's features.
)