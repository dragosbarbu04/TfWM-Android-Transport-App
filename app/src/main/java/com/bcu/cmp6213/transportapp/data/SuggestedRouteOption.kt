// File: com/bcu/cmp6213/transportapp/data/SuggestedRouteOption.kt
package com.bcu.cmp6213.transportapp.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a single, direct route option suggested to the user for travel between
 * a determined origin and destination.
 *
 * This class encapsulates all necessary details for displaying one travel suggestion,
 * combining information from `GtfsRoute`, `Stop` (for specific origin and destination stops),
 * and `Trip` (via `tripId` and associated timings).
 *
 * It implements Parcelable using the `@Parcelize` annotation, which allows instances of this class
 * to be easily passed between Android components, such as Activities or Fragments,
 * if needed (e.g., sending a selected option to a detailed confirmation screen).
 *
 * @property route The [GtfsRoute] object containing general information about the suggested route (e.g., name, type).
 * @property originStop The specific [Stop] object representing the boarding point for this suggested option,
 * which is near the user's overall origin.
 * @property destinationStop The specific [Stop] object representing the alighting point for this suggested option,
 * which is near the user's overall destination.
 * @property tripId The unique identifier (`trip_id` from GTFS `trips.txt`) for the specific trip
 * that services the journey between the `originStop` and `destinationStop`
 * at the given times. This is crucial for fetching trip-specific details like its exact path (shape).
 * Nullable if a trip ID couldn't be determined or isn't applicable.
 * @property originDepartureTime The scheduled departure time from the `originStop` for this specific `tripId`.
 * Stored as a String (e.g., "HH:MM:SS" from GTFS), nullable if not available.
 * @property destinationArrivalTime The scheduled arrival time at the `destinationStop` for this specific `tripId`.
 * Stored as a String (e.g., "HH:MM:SS" from GTFS), nullable if not available.
 */
@Parcelize // Annotation to automatically generate the Parcelable implementation.
data class SuggestedRouteOption(
    /**
     * The [GtfsRoute] object providing details about the overall route,
     * such as its short name (e.g., "X1"), long name, and type (e.g., bus, tram).
     */
    val route: GtfsRoute,

    /**
     * The specific [Stop] where the user would board for this leg of the journey.
     * This stop is identified as being close to the user's requested origin point.
     */
    val originStop: Stop,

    /**
     * The specific [Stop] where the user would alight for this leg of the journey.
     * This stop is identified as being close to the user's requested destination point.
     */
    val destinationStop: Stop,

    /**
     * The identifier of the specific GTFS trip (`trip_id`) that operates this service
     * between the `originStop` and `destinationStop` at the specified times.
     * This is essential for linking to other GTFS data, such as `shapes.txt` for drawing
     * the route path on a map. It's nullable in case a trip ID isn't directly associated
     * or if the suggestion is more abstract.
     */
    val tripId: String?,

    /**
     * The scheduled departure time from the `originStop` for this particular trip.
     * The format is typically "HH:MM:SS" as found in GTFS `stop_times.txt`.
     * It can be null if the time is not specified or not applicable.
     */
    val originDepartureTime: String?,

    /**
     * The scheduled arrival time at the `destinationStop` for this particular trip.
     * The format is typically "HH:MM:SS" as found in GTFS `stop_times.txt`.
     * It can be null if the time is not specified or not applicable.
     */
    val destinationArrivalTime: String?
) : Parcelable // Implements Parcelable, allowing objects of this class to be efficiently passed around in the Android framework.