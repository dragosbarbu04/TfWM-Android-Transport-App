// File: com/bcu/cmp6213/transportapp/data/ShapePoint.kt
package com.bcu.cmp6213.transportapp.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a single geographic point that forms part of a physical route path (a "shape")
 * from the GTFS (General Transit Feed Specification) `shapes.txt` file.
 *
 * A shape is defined by a sequence of these points. When connected in order, they draw
 * the path a vehicle takes. This class is often used to draw polylines on a map.
 *
 * This class implements Parcelable using the `@Parcelize` annotation, allowing instances
 * to be easily passed between Android components (e.g., via Intent extras or Bundles).
 *
 * Fields from `shapes.txt`:
 * - `shape_id`: Identifies a specific shape (a unique path). (Required)
 * - `shape_pt_lat`: Latitude of this specific point in the shape. (Required)
 * - `shape_pt_lon`: Longitude of this specific point in the shape. (Required)
 * - `shape_pt_sequence`: The order of this point within the shape. Points are connected in increasing sequence. (Required)
 * - `shape_dist_traveled`: The distance traveled along the shape from its first point to this point. (Optional)
 */
@Parcelize // Annotation to automatically generate the Parcelable implementation.
data class ShapePoint(
    /**
     * An identifier that uniquely references a specific shape (i.e., a path or sequence of points).
     * All points with the same `shapeId` belong to the same path. This ID is referenced by `trips.txt`.
     */
    val shapeId: String,

    /**
     * The WGS84 latitude of this specific point on the shape.
     * Values are typically in decimal degrees.
     */
    val shapePtLat: Double,

    /**
     * The WGS84 longitude of this specific point on the shape.
     * Values are typically in decimal degrees.
     */
    val shapePtLon: Double,

    /**
     * An integer that indicates the order of this point in the sequence of points
     * that define the shape. The sequence must be non-decreasing.
     * Points are connected in the order of their `shapePtSequence`.
     */
    val shapePtSequence: Int,

    /**
     * An optional field that records the cumulative distance traveled along the shape
     * from the first point (sequence 0 or the lowest sequence number) to this point.
     * This can be used for various purposes, such as placing stop icons accurately along a
     * shape or interpolating positions. Units are typically meters or kilometers.
     * It's nullable (`Double?`) because this field is optional in `shapes.txt`.
     */
    val shapeDistTraveled: Double?
) : Parcelable // Implements Parcelable, allowing objects of this class to be efficiently passed between Android components.