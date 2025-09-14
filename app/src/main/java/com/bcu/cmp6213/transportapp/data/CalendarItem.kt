package com.bcu.cmp6213.transportapp.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a service availability pattern from the GTFS (General Transit Feed Specification)
 * `calendar.txt` file.
 *
 * This class defines a regular service schedule, indicating which days of the week a particular
 * service operates and the date range for which this schedule is valid.
 * It works in conjunction with `CalendarDateItem` (from `calendar_dates.txt`),
 * which defines exceptions to this regular schedule.
 *
 * This class implements Parcelable, allowing instances to be passed between Android components
 * like Activities or Fragments (e.g., in Intent extras or Bundle arguments).
 *
 * Fields from `calendar.txt`:
 * - `service_id`: A unique identifier for a specific set of service dates. (Required) [cite: 1]
 * - `monday`, `tuesday`, ..., `sunday`: Boolean values (represented as 0 or 1 in the GTFS file)
 * indicating if the service is available on that particular day of the week. (Required) [cite: 1]
 * - `start_date`: The start date for this service pattern, formatted as YYYYMMDD. (Required) [cite: 1]
 * - `end_date`: The end date for this service pattern, formatted as YYYYMMDD. (Required) [cite: 1]
 */
@Parcelize // Annotation to automatically generate the Parcelable implementation.
data class CalendarItem(
    /**
     * A unique ID that identifies a set of dates when service is available for one or more routes.
     * This `serviceId` is referenced by `trips.txt` and `calendar_dates.txt`.
     */
    val serviceId: String,

    /**
     * Indicates if the service is available on Mondays within the date range.
     * (True if available, False otherwise).
     */
    val monday: Boolean,

    /**
     * Indicates if the service is available on Tuesdays within the date range.
     * (True if available, False otherwise).
     */
    val tuesday: Boolean,

    /**
     * Indicates if the service is available on Wednesdays within the date range.
     * (True if available, False otherwise).
     */
    val wednesday: Boolean,

    /**
     * Indicates if the service is available on Thursdays within the date range.
     * (True if available, False otherwise).
     */
    val thursday: Boolean,

    /**
     * Indicates if the service is available on Fridays within the date range.
     * (True if available, False otherwise).
     */
    val friday: Boolean,

    /**
     * Indicates if the service is available on Saturdays within the date range.
     * (True if available, False otherwise).
     */
    val saturday: Boolean,

    /**
     * Indicates if the service is available on Sundays within the date range.
     * (True if available, False otherwise).
     */
    val sunday: Boolean,

    /**
     * The start date for the service pattern.
     * Service defined by this pattern is active from this date onwards,
     * until the `endDate`. The format is YYYYMMDD (e.g., "20250101" for January 1, 2025).
     */
    val startDate: String, // Format: YYYYMMDD

    /**
     * The end date for the service pattern.
     * Service defined by this pattern is active up to and including this date.
     * The format is YYYYMMDD (e.g., "20251231" for December 31, 2025).
     */
    val endDate: String    // Format: YYYYMMDD
) : Parcelable // Implements Parcelable, allowing objects of this class to be efficiently passed between Android components.