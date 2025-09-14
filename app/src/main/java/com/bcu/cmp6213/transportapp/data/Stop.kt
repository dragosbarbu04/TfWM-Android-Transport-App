package com.bcu.cmp6213.transportapp.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a stop, station, or other point of interest from the GTFS (General Transit Feed Specification)
 * `stops.txt` file.
 *
 * This class defines locations where vehicles pick up or drop off passengers. It can also represent
 * stations composed of multiple stops, or points like entrances/exits.
 * It implements Parcelable to allow instances to be passed between Android components.
 *
 * Key fields from `stops.txt`:
 * - `stop_id`: Unique identifier for the stop/station. (Required in GTFS)
 * - `stop_code`: Short text or number uniquely identifying the stop for passengers (e.g., a bus stop code displayed on a sign). (Optional)
 * - `stop_name`: Name of the stop/station. (Conditionally Required in GTFS for stops/stations)
 * - `stop_desc`: Description of the stop/station. (Optional)
 * - `stop_lat`: Latitude of the stop/station. (Conditionally Required in GTFS for stops/stations)
 * - `stop_lon`: Longitude of the stop/station. (Conditionally Required in GTFS for stops/stations)
 * - `zone_id`: Fare zone for the stop/station. (Optional, used in fare calculation)
 * - `stop_url`: URL with more information about the stop/station. (Optional)
 * - `location_type`: Identifies whether this entry represents a stop, station, entrance/exit, generic node, or boarding area.
 * (0 or empty = Stop/Platform, 1 = Station, 2 = Entrance/Exit, 3 = Generic Node, 4 = Boarding Area). (Optional)
 * - `parent_station`: ID of the parent station if this entry is part of a larger station complex (e.g., a platform within a station). (Optional)
 */
@Parcelize // Annotation to automatically generate the Parcelable implementation.
data class Stop(
    /**
     * Unique identifier for a stop or station. This ID is referenced by `stop_times.txt`.
     * Required by GTFS.
     */
    val stopId: String,

    /**
     * A short, human-readable code for the stop, often displayed on signage.
     * Examples: "S123", "BUS10". Optional in GTFS.
     */
    val stopCode: String?,

    /**
     * The name of the stop or station as displayed to the public.
     * Example: "New Street Station", "High St (Stop A)".
     * Conditionally required by GTFS for physical stops/stations (`location_type` 0 or 1).
     * Nullable here to accommodate other location types or gracefully handle missing data.
     */
    val stopName: String?,

    /**
     * A more detailed description of the stop, if needed.
     * Could include landmarks or specific location details. Optional in GTFS.
     */
    val stopDesc: String?,

    /**
     * The WGS84 latitude of the stop or station.
     * Conditionally required by GTFS for physical stops/stations (`location_type` 0 or 1).
     * Nullable here as a String, which would be parsed to Double when used. Accommodates
     * other location types or gracefully handles missing data.
     */
    val stopLat: String?,

    /**
     * The WGS84 longitude of the stop or station.
     * Conditionally required by GTFS for physical stops/stations (`location_type` 0 or 1).
     * Nullable here as a String, which would be parsed to Double when used. Accommodates
     * other location types or gracefully handles missing data.
     */
    val stopLon: String?,

    /**
     * An integer indicating the type of location:
     * - 0 (or empty): Stop (a platform or specific point where vehicles stop).
     * - 1: Station (a physical structure or area that contains one or more stops/platforms).
     * - 2: Entrance/Exit (a location representing an entrance or exit to a station).
     * - 3: Generic Node (a location within a station, not used by passengers for boarding/alighting).
     * - 4: Boarding Area (a specific location on a platform where passengers can board).
     * Optional in GTFS (defaults to 0 if omitted).
     */
    val locationType: String?, // Represents an integer but read as String from GTFS.

    /**
     * The `stop_id` of the parent station if this stop is part of a station complex.
     * For example, a platform (`location_type`=0) can have a `parent_station` that is a station building (`location_type`=1).
     * Optional in GTFS.
     */
    val parentStation: String?

    // Other optional fields from stops.txt like `zone_id`, `stop_url`, `wheelchair_boarding`,
    // `stop_timezone`, etc., can be added here if needed by the application.
) : Parcelable // Implements Parcelable, making objects of this class suitable for being passed in Intents or Bundles.