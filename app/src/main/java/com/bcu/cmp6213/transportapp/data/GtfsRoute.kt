package com.bcu.cmp6213.transportapp.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a single route from the GTFS (General Transit Feed Specification) `routes.txt` file.
 * A route is a group of trips that are displayed to riders as a single service.
 *
 * This class implements Parcelable using the `@Parcelize` annotation, allowing instances
 * to be easily passed between Android components (e.g., via Intent extras or Bundles).
 *
 * The `routes.txt` file typically contains columns such as:
 * - `route_id`: Unique identifier for the route. (Required in GTFS)
 * - `agency_id`: Identifier for the agency providing the service. (Optional in GTFS, but often present and useful)
 * - `route_short_name`: A short name for the route (e.g., "11A", "X1", "Metro Line A"). (Often present)
 * - `route_long_name`: A longer, more descriptive name for the route (e.g., "Outer Circle Anti-Clockwise", "Birmingham to Coventry Express Service"). (Often present)
 * - `route_desc`: A description of the route, providing additional information not covered by short or long names. (Optional)
 * - `route_type`: An integer code indicating the type of transport (e.g., 0 for Tram, 1 for Subway/Metro, 2 for Rail, 3 for Bus). (Required in GTFS)
 * - `route_url`: A URL with more information about the route. (Optional)
 * - `route_color`: A color to be used when displaying this route on a map (hexadecimal, e.g., "FFFFFF"). (Optional)
 * - `route_text_color`: A text color to be used for the route's name or headsign on a map (hexadecimal). (Optional)
 */
@Parcelize // Enables automatic implementation of the Parcelable interface.
data class GtfsRoute(
    /**
     * Unique identifier for this route. This ID is used to associate this route
     * with trips in `trips.txt`.
     */
    val routeId: String,

    /**
     * Identifier of the agency that operates this route.
     * This field is optional in the GTFS specification, so it's nullable.
     * It links to an agency defined in `agency.txt`.
     */
    val agencyId: String?,

    /**
     * A short name for the route, often a number or a short code (e.g., "101", "X4").
     * This is what passengers often use to identify a route. Nullable if not provided.
     */
    val routeShortName: String?,

    /**
     * The full, descriptive name of the route (e.g., "High Street to University Campus").
     * This can be more descriptive than `routeShortName`. Nullable if not provided.
     */
    val routeLongName: String?,

    /**
     * The type of transport used on this route.
     * GTFS defines specific integer codes:
     * 0: Tram, Streetcar, Light rail
     * 1: Subway, Metro
     * 2: Rail (Intercity or long-distance travel)
     * 3: Bus
     * 4: Ferry
     * 5: Cable tram
     * 6: Aerial lift, suspended cable car (gondola, cable car)
     * 7: Funicular
     * 11: Trolleybus
     * 12: Monorail
     * (And others for extended route types)
     * Stored as String? as it's read from text file, but represents an integer. Nullable if not provided.
     */
    val routeType: String?,

    // Other optional fields from routes.txt can be added here if they are needed
    // for specific application features. For example:
    // val routeDesc: String?,      // Description of the route.
    // val routeUrl: String?,       // URL with more information about the route.
    // val routeColor: String?,     // Route color for map display (e.g., "FF0000" for red).
    // val routeTextColor: String? // Text color for route display (e.g., "FFFFFF" for white).

) : Parcelable // Implements Parcelable, making objects of this class suitable for being passed in Intents or Bundles.