package com.bcu.cmp6213.transportapp.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents an exception or addition to service dates from the GTFS (General Transit Feed Specification)
 * `calendar_dates.txt` file.
 *
 * This class defines specific dates when a service is either made available (if it wouldn't normally run)
 * or made unavailable (if it would normally run according to `calendar.txt`).
 * It implements Parcelable to allow instances of this class to be passed between Android components,
 * for example, in Intent extras or Fragment arguments.
 *
 * Fields from `calendar_dates.txt`:
 * - `service_id`: Identifies the service pattern (from `calendar.txt`) affected by this date exception. (Required)
 * - `date`: The specific date of the exception, formatted as YYYYMMDD. (Required)
 * - `exception_type`: An integer indicating whether the service is added (1) or removed (2) on this date. (Required)
 */
@Parcelize // Annotation to automatically generate the Parcelable implementation.
data class CalendarDateItem(
    /**
     * Unique identifier for a service pattern. This ID links this exception
     * to a specific set of service days defined in `calendar.txt`.
     */
    val serviceId: String,

    /**
     * The specific date when the exception applies.
     * The format is YYYYMMDD (e.g., "20251225" for December 25, 2025).
     */
    val date: String, // Format: YYYYMMDD

    /**
     * Defines the nature of the exception for the given `serviceId` on the specified `date`.
     * - 1: Service is ADDED for this date. (e.g., a special holiday service runs on a day it normally wouldn't)
     * - 2: Service is REMOVED for this date. (e.g., a regular weekday service is cancelled due to a holiday)
     */
    val exceptionType: Int
) : Parcelable // Implements Parcelable, allowing objects of this class to be written to and restored from a Parcel.