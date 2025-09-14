// File: app/src/main/java/com/bcu/cmp6213/transportapp/viewmodel/GtfsViewModel.kt
package com.bcu.cmp6213.transportapp.viewmodel

// Android and Java/Kotlin utility imports
import android.app.Application // Base class for maintaining global application state.
import android.location.Location // Represents a geographic location.
import android.os.Build // Provides information about the current device's build.
import android.util.Log // For logging.
import androidx.annotation.RequiresApi // Indicates that a method/class requires a specific API level.
import androidx.lifecycle.AndroidViewModel // ViewModel variant that includes an Application reference.
import androidx.lifecycle.LiveData // Data holder class that can be observed within a given lifecycle.
import androidx.lifecycle.MutableLiveData // LiveData that can be modified.
import androidx.lifecycle.viewModelScope // Coroutine scope tied to the ViewModel's lifecycle.

// App-specific data model imports
import com.bcu.cmp6213.transportapp.data.CalendarDateItem
import com.bcu.cmp6213.transportapp.data.CalendarItem
import com.bcu.cmp6213.transportapp.data.GtfsRoute
import com.bcu.cmp6213.transportapp.data.Stop
import com.bcu.cmp6213.transportapp.data.StopTime
import com.bcu.cmp6213.transportapp.data.SuggestedRouteOption
import com.bcu.cmp6213.transportapp.data.Trip
// Note: ShapePoint import was commented out, assuming LatLng from Google Maps is used directly for shapes.
// import com.bcu.cmp6213.transportapp.data.ShapePoint

// Network service import
import com.bcu.cmp6213.transportapp.network.TfwmApiService

// Google Maps import
import com.google.android.gms.maps.model.LatLng // Represents a geographical coordinate for Google Maps.

// Coroutine imports
import kotlinx.coroutines.Dispatchers // For managing coroutine execution contexts (e.g., IO, Default, Main).
import kotlinx.coroutines.launch // Builder for launching new coroutines.
import kotlinx.coroutines.withContext // For switching coroutine contexts.
import kotlinx.coroutines.yield // For yielding execution to other coroutines, improving responsiveness.

// OkHttp import (used by Retrofit)
import okhttp3.ResponseBody // Represents the raw response body from an HTTP request.

// Java I/O and utility imports
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File // For file system operations.
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException // For handling I/O exceptions.
import java.io.InputStream
import java.io.OutputStream
import java.time.DayOfWeek // For working with days of the week (Java 8+ time API).
import java.time.LocalDate // For dates without time (Java 8+ time API).
import java.time.LocalTime // For time without dates (Java 8+ time API).
import java.time.format.DateTimeFormatter // For parsing and formatting dates/times (Java 8+ time API).
import java.time.format.DateTimeParseException // For handling errors during date/time parsing.
import java.util.Locale // For locale-specific operations like string formatting.
import java.util.zip.ZipEntry // For handling entries within a ZIP archive.
import java.util.zip.ZipInputStream // For reading ZIP archives.
import kotlin.math.* // For mathematical functions like sin, cos, atan2, sqrt.

/**
 * [AndroidViewModel] responsible for fetching, parsing, processing, and providing GTFS data
 * for the TfWM transport network.
 *
 * This ViewModel handles:
 * - Downloading the GTFS zip file from the TfWM API.
 * - Unzipping and caching the GTFS text files.
 * - Parsing individual GTFS files (routes, trips, stops, stop_times, calendar, calendar_dates, shapes).
 * - Storing parsed GTFS data in memory.
 * - Providing LiveData streams for UI components to observe (e.g., filtered routes, route details, suggestions).
 * - Implementing business logic such as route searching, stop sequence retrieval, shape path loading,
 * service availability checks, and generating direct route suggestions.
 *
 * It uses [TfwmApiService] for network operations and coroutines for background tasks.
 *
 * @param application The application context, available through AndroidViewModel.
 */
class GtfsViewModel(application: Application) : AndroidViewModel(application) {

    // Instance of the Retrofit service for TfWM API communication.
    private val tfwmApiService = TfwmApiService.create()

    // --- In-Memory Data Stores for Parsed GTFS Data ---
    // @Volatile ensures that writes to these variables are immediately visible to other threads.
    // Useful if these were accessed from multiple threads directly, though primary access is via LiveData from UI thread.
    @Volatile private var allDeDuplicatedRoutes: List<GtfsRoute> = emptyList()
    @Volatile private var allTrips: List<Trip> = emptyList()
    @Volatile private var stopTimesByTripId: Map<String, List<StopTime>> = emptyMap() // Key: trip_id
    @Volatile private var stopsById: Map<String, Stop> = emptyMap() // Key: stop_id
    @Volatile private var calendarItems: List<CalendarItem> = emptyList() // Regular service schedules
    @Volatile private var calendarDateExceptionsByServiceId: Map<String, List<CalendarDateItem>> = emptyMap() // Service exceptions, Key: service_id

    // LiveData properties exposed to the UI (Fragments/Activities).
    // These are typically observed by UI components to react to data changes.
    // Private MutableLiveData for internal updates, public LiveData for external observation.

    // LiveData for the list of routes, potentially filtered by search queries.
    private val _filteredRoutes = MutableLiveData<List<GtfsRoute>>()
    val filteredRoutes: LiveData<List<GtfsRoute>> = _filteredRoutes

    // LiveData for the sequence of stops for a currently selected route. Nullable if no route selected or no stops.
    private val _selectedRouteStopSequence = MutableLiveData<List<Stop>?>()
    val selectedRouteStopSequence: LiveData<List<Stop>?> = _selectedRouteStopSequence

    // LiveData for the geographical points (shape) of a selected trip's path. Nullable.
    private val _selectedTripShapePoints = MutableLiveData<List<LatLng>?>()
    val selectedTripShapePoints: LiveData<List<LatLng>?> = _selectedTripShapePoints

    // LiveData for suggested route options based on user's origin and destination. Nullable.
    private val _suggestedRouteOptions = MutableLiveData<List<SuggestedRouteOption>?>()
    val suggestedRouteOptions: LiveData<List<SuggestedRouteOption>?> = _suggestedRouteOptions

    // LiveData for global loading state (e.g., when downloading/parsing all GTFS data).
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // LiveData for loading state when fetching details for a specific route (e.g., its stop sequence).
    private val _isLoadingDetails = MutableLiveData<Boolean>()
    val isLoadingDetails: LiveData<Boolean> = _isLoadingDetails

    // LiveData for loading state when generating route suggestions.
    private val _isLoadingSuggestions = MutableLiveData<Boolean>()
    val isLoadingSuggestions: LiveData<Boolean> = _isLoadingSuggestions

    // LiveData for loading state when fetching shape points for a specific trip.
    private val _isLoadingShape = MutableLiveData<Boolean>()
    val isLoadingShape: LiveData<Boolean> = _isLoadingShape

    // LiveData for general error messages related to GTFS loading/parsing. Nullable.
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // LiveData for error messages specific to loading route details. Nullable.
    private val _detailError = MutableLiveData<String?>()
    val detailError: LiveData<String?> = _detailError

    // LiveData for error messages specific to generating route suggestions. Nullable.
    private val _suggestionError = MutableLiveData<String?>()
    val suggestionError: LiveData<String?> = _suggestionError

    // Companion object for constants used within the ViewModel.
    companion object {
        private const val TAG = "GtfsViewModel" // Log tag.
        // Filenames and directory names for GTFS data management.
        private const val GTFS_ZIP_FILE_NAME = "tfwm_gtfs.zip"
        private const val GTFS_UNZIPPED_DIR_NAME = "gtfs_unzipped"
        // Standard GTFS file names.
        private const val FILE_ROUTES = "routes.txt"
        private const val FILE_TRIPS = "trips.txt"
        private const val FILE_STOP_TIMES = "stop_times.txt"
        private const val FILE_STOPS = "stops.txt"
        private const val FILE_CALENDAR = "calendar.txt"
        private const val FILE_CALENDAR_DATES = "calendar_dates.txt"
        private const val FILE_SHAPES = "shapes.txt"

        // Constants for geographical calculations and route suggestion logic.
        private const val EARTH_RADIUS_METERS = 6371000.0 // Earth's mean radius in meters.
        private const val NEARBY_STOP_RADIUS_METERS = 1000.0 // Radius for finding nearby stops (1 km).

        // DateTimeFormatters for parsing GTFS dates (YYYYMMDD) and times (HH:MM:SS).
        // Requires API level 26 (Android Oreo) or higher for java.time classes.
        @RequiresApi(Build.VERSION_CODES.O)
        private val GTFS_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        @RequiresApi(Build.VERSION_CODES.O)
        private val GTFS_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }

    /**
     * Loads all necessary GTFS data.
     * This involves checking for existing data (in-memory or unzipped files),
     * downloading the GTFS zip file if needed (or if `forceDownload` is true),
     * unzipping it, and parsing the relevant text files.
     * Updates LiveData for loading state and errors.
     *
     * @param forceDownload If true, forces a fresh download and parsing of GTFS data,
     * bypassing any cached data. Defaults to false.
     */
    fun loadGtfsData(forceDownload: Boolean = false) {
        // Prevent multiple concurrent loads unless forced.
        if (_isLoading.value == true && !forceDownload) {
            Log.d(TAG, "loadGtfsData: Load already in progress and not forced. Skipping.")
            return
        }
        _isLoading.value = true // Set loading state to true.
        _error.value = null     // Clear any previous errors.

        viewModelScope.launch { // Launch a coroutine in the ViewModel's scope.
            val gtfsDir = getApplication<Application>().cacheDir // Use app's cache directory for GTFS files.
            val unzippedFilesDir = File(gtfsDir, GTFS_UNZIPPED_DIR_NAME) // Directory for unzipped files.

            if (forceDownload) {
                Log.d(TAG, "loadGtfsData: Force download initiated.")
                clearAllParsedDataAndYield() // Clear existing parsed data if forcing download.
            } else {
                // Check if essential data is already in memory (excluding shapes, which are loaded on demand).
                if (allDeDuplicatedRoutes.isNotEmpty() && stopsById.isNotEmpty() &&
                    stopTimesByTripId.isNotEmpty() && allTrips.isNotEmpty() &&
                    calendarItems.isNotEmpty()) {
                    Log.d(TAG, "loadGtfsData: Using existing in-memory data. Shapes will be loaded on demand.")
                    _filteredRoutes.postValue(allDeDuplicatedRoutes) // Update LiveData with existing routes.
                    _isLoading.postValue(false) // Set loading state to false.
                    return@launch // Exit if data is already in memory.
                }
                // Check if essential unzipped files exist on disk.
                // shapes.txt is also checked here for a complete local cache, though parsed on demand.
                val essentialFilesForList = listOf(FILE_ROUTES, FILE_TRIPS, FILE_STOP_TIMES, FILE_STOPS, FILE_CALENDAR, FILE_SHAPES)
                if (essentialFilesForList.all { File(unzippedFilesDir, it).exists() }) {
                    Log.d(TAG, "loadGtfsData: Attempting to parse from existing unzipped files. Shapes will be loaded on demand.")
                    try {
                        clearAllParsedDataAndYield() // Clear any potentially stale in-memory data first.
                        parseAllRequiredGtfsFiles(unzippedFilesDir) // Parse from disk.
                        // Check if parsing yielded any data.
                        if (allDeDuplicatedRoutes.isNotEmpty() || stopsById.isNotEmpty() || allTrips.isNotEmpty() || calendarItems.isNotEmpty()) {
                            _filteredRoutes.postValue(allDeDuplicatedRoutes)
                            Log.d(TAG, "loadGtfsData: Successfully parsed from existing unzipped files.")
                            _isLoading.postValue(false)
                            return@launch
                        } else {
                            Log.w(TAG, "loadGtfsData: Parsing existing unzipped files yielded no primary data. Proceeding to download.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "loadGtfsData: Error parsing existing unzipped files. Proceeding to download.", e)
                        clearAllParsedDataAndYield() // Clear data on parsing error.
                    }
                } else {
                    Log.d(TAG, "loadGtfsData: Not all essential unzipped GTFS files exist. Proceeding to download.")
                }
            }

            // If not using existing data (either forced or cache miss), clear data before download.
            // This was already called for forceDownload, added for clarity for cache miss.
            if (!forceDownload && (allDeDuplicatedRoutes.isNotEmpty() || allTrips.isNotEmpty())) { // Redundant if logic above is perfect
                Log.d(TAG, "loadGtfsData: Cache miss or incomplete data. Clearing before download.")
                clearAllParsedDataAndYield()
            }

            Log.d(TAG, "loadGtfsData: Starting GTFS zip file download.")
            val zipFile = File(gtfsDir, GTFS_ZIP_FILE_NAME) // Target file for the downloaded zip.

            try {
                // Perform network request to download the GTFS zip file.
                val response = tfwmApiService.downloadGtfsZip()
                if (response.isSuccessful && response.body() != null) {
                    // If download is successful, save the file.
                    if (saveFile(response.body()!!, zipFile)) {
                        Log.d(TAG, "loadGtfsData: Download successful. Saved to ${zipFile.absolutePath}")
                        // Delete old unzipped directory if it exists, then recreate it.
                        if (unzippedFilesDir.exists()) {
                            unzippedFilesDir.deleteRecursively()
                        }
                        if (!unzippedFilesDir.mkdirs()) {
                            _error.postValue("Failed to create directory for unzipped files.")
                            clearAllParsedDataAndYield()
                            _isLoading.postValue(false)
                            return@launch
                        }
                        unzip(zipFile, unzippedFilesDir) // Unzip the downloaded file.
                        Log.d(TAG, "loadGtfsData: Unzip complete.")
                        // Parse the newly unzipped files (excluding shapes.txt, which is on-demand).
                        parseAllRequiredGtfsFiles(unzippedFilesDir)
                        _filteredRoutes.postValue(allDeDuplicatedRoutes) // Update LiveData.
                    } else {
                        _error.postValue("Failed to save downloaded GTFS data.")
                        clearAllParsedDataAndYield()
                    }
                } else {
                    // Handle download error (e.g., non-2xx HTTP response).
                    _error.postValue("Download error (Code: ${response.code()} - ${response.message()})")
                    clearAllParsedDataAndYield()
                }
            } catch (oom: OutOfMemoryError) { // Catch OOM specifically, as GTFS processing can be memory intensive.
                Log.e(TAG, "loadGtfsData: OutOfMemoryError during load process.", oom)
                _error.postValue("Out of memory while loading data.")
                clearAllParsedDataAndYield()
            }
            catch (e: Exception) { // Catch any other exceptions during the process.
                Log.e(TAG, "loadGtfsData: Exception during load process.", e)
                _error.postValue("Load failed: ${e.localizedMessage}")
                clearAllParsedDataAndYield()
            }
            finally {
                _isLoading.postValue(false) // Ensure loading state is set to false in all cases.
            }
        }
    }

    /**
     * Parses all required GTFS text files from the specified directory,
     * except for `shapes.txt` which is parsed on-demand.
     * Populates the in-memory data stores.
     * @param unzippedFilesDir The directory containing the unzipped GTFS text files.
     */
    private suspend fun parseAllRequiredGtfsFiles(unzippedFilesDir: File) {
        Log.d(TAG, "parseAllRequiredGtfsFiles: Starting parsing of core GTFS files.")
        // Map of filenames to File objects for easier access.
        val files = mapOf(
            FILE_ROUTES to File(unzippedFilesDir, FILE_ROUTES),
            FILE_TRIPS to File(unzippedFilesDir, FILE_TRIPS),
            FILE_STOP_TIMES to File(unzippedFilesDir, FILE_STOP_TIMES),
            FILE_STOPS to File(unzippedFilesDir, FILE_STOPS),
            FILE_CALENDAR to File(unzippedFilesDir, FILE_CALENDAR),
            FILE_CALENDAR_DATES to File(unzippedFilesDir, FILE_CALENDAR_DATES)
            // shapes.txt is NOT parsed here; it's handled by readShapePointsFromFile on demand.
        )

        // Check if essential files exist.
        val essentialFiles = listOf(FILE_ROUTES, FILE_TRIPS, FILE_STOP_TIMES, FILE_STOPS, FILE_CALENDAR)
        if (essentialFiles.any { !files[it]!!.exists() }) {
            _error.postValue("One or more essential GTFS files are missing after unzipping.")
            clearAllParsedDataAndYield()
            return
        }

        try {
            // Parse each file sequentially. `yield()` allows the coroutine to be cooperative
            // and give other tasks a chance to run, preventing UI freezes on large datasets.
            yield(); allDeDuplicatedRoutes = parseRoutesTxt(files[FILE_ROUTES]!!)
            yield(); allTrips = parseTripsTxt(files[FILE_TRIPS]!!)
            yield(); stopsById = parseStopsTxt(files[FILE_STOPS]!!) // Must be parsed before stop_times
            yield(); calendarItems = parseCalendarTxt(files[FILE_CALENDAR]!!)
            yield(); calendarDateExceptionsByServiceId = parseCalendarDatesTxt(files[FILE_CALENDAR_DATES]!!)
            yield(); stopTimesByTripId = parseStopTimesTxt(files[FILE_STOP_TIMES]!!) // Parsed last as it might reference other data.

            Log.d(TAG, "parseAllRequiredGtfsFiles: Parsing complete. " +
                    "Routes: ${allDeDuplicatedRoutes.size}, Trips: ${allTrips.size}, " +
                    "Stops: ${stopsById.size}, Calendar: ${calendarItems.size}, " +
                    "CalendarDates: ${calendarDateExceptionsByServiceId.values.sumOf { it.size }}, " +
                    "StopTimes entries: ${stopTimesByTripId.values.sumOf { it.size }}")
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "parseAllRequiredGtfsFiles: OutOfMemoryError during parsing.", oom)
            _error.postValue("Out of memory during data parsing.")
            clearAllParsedDataAndYield()
        }
        catch (e: Exception) {
            Log.e(TAG, "parseAllRequiredGtfsFiles: Exception during parsing.", e)
            _error.postValue("Error parsing GTFS files: ${e.localizedMessage}")
            clearAllParsedDataAndYield()
        }
    }

    /**
     * Clears all parsed GTFS data from memory and LiveData objects.
     * Calls `yield()` to allow other coroutines to run.
     */
    private suspend fun clearAllParsedDataAndYield() {
        Log.d(TAG, "clearAllParsedDataAndYield: Clearing all parsed GTFS data from memory.")
        allDeDuplicatedRoutes = emptyList()
        _filteredRoutes.postValue(emptyList()) // Update LiveData
        allTrips = emptyList()
        stopTimesByTripId = emptyMap()
        stopsById = emptyMap()
        calendarItems = emptyList()
        calendarDateExceptionsByServiceId = emptyMap()
        // Clear LiveData for specific selections as well.
        _selectedRouteStopSequence.postValue(null)
        _suggestedRouteOptions.postValue(null)
        _selectedTripShapePoints.postValue(null)
        yield() // Yield execution.
    }

    /**
     * Reads and parses `shapes.txt` ON DEMAND for a specific `shapeIdToFind`.
     * This avoids loading all shape data into memory at once, which caused OOM errors.
     *
     * @param shapeIdToFind The specific `shape_id` to look for in `shapes.txt`.
     * @param shapesFile The [File] object pointing to `shapes.txt`.
     * @return A list of [LatLng] points for the requested shape, sorted by sequence.
     * Returns an empty list if the shape is not found, the file is missing/unreadable,
     * or an error occurs.
     */
    private suspend fun readShapePointsFromFile(shapeIdToFind: String, shapesFile: File): List<LatLng> {
        // Execute file reading on the IO dispatcher for background processing.
        return withContext(Dispatchers.IO) {
            val pointsForShape = mutableListOf<Pair<Int, LatLng>>() // Temporary list to hold (sequence, LatLng) for sorting.
            Log.d(TAG, "readShapePointsFromFile: Reading shape points for shape_id '$shapeIdToFind' from ${shapesFile.name}")

            // Check if shapes.txt exists and is readable.
            if (!shapesFile.exists() || !shapesFile.canRead()) {
                Log.e(TAG, "readShapePointsFromFile: ${shapesFile.name} not found or unreadable for shape_id '$shapeIdToFind'.")
                _error.postValue("Shapes data file (${FILE_SHAPES}) not available.") // Post error for UI.
                return@withContext emptyList() // Return empty list on file error.
            }

            var headerLine: String? = null
            var columnMap: Map<String, Int>? = null // To store header column names and their indices.
            // Indices for required columns.
            var shapeIdIdx = -1
            var latIdx = -1
            var lonIdx = -1
            var sequenceIdx = -1

            try {
                // Use useLines for efficient line-by-line reading, ensuring the reader is closed.
                BufferedReader(FileReader(shapesFile)).useLines { lines ->
                    for (line in lines) {
                        yield() // Yield to allow other coroutines and prevent UI freeze on very large files.
                        if (headerLine == null) {
                            // First line is the header. Parse it to map column names to indices.
                            headerLine = line
                            val headers = headerLine!!.split(",").map { it.trim() }
                            columnMap = headers.mapIndexed { index, h -> h to index }.toMap()
                            // Get indices for essential columns.
                            shapeIdIdx = columnMap!!["shape_id"] ?: -1
                            latIdx = columnMap!!["shape_pt_lat"] ?: -1
                            lonIdx = columnMap!!["shape_pt_lon"] ?: -1
                            sequenceIdx = columnMap!!["shape_pt_sequence"] ?: -1
                            // Validate that all essential columns were found.
                            if (listOf(shapeIdIdx, latIdx, lonIdx, sequenceIdx).any { it == -1 }) {
                                Log.e(TAG, "readShapePointsFromFile: Essential shape columns (shape_id, shape_pt_lat, shape_pt_lon, shape_pt_sequence) missing in ${shapesFile.name}.")
                                _error.postValue("Shapes file format error (missing essential columns).")
                                return@withContext emptyList() // Return empty if columns are missing.
                            }
                            continue // Skip to the next line after processing the header.
                        }

                        if (line.isNullOrBlank()) continue // Skip empty lines.

                        // Split data line into tokens. Trim whitespace and remove surrounding quotes.
                        val tokens = line.split(",").map { it.trim().removeSurrounding("\"") }
                        try {
                            // Check if the current line's shape_id matches the one we are looking for.
                            if (tokens.getOrNull(shapeIdIdx) == shapeIdToFind) {
                                val lat = tokens.getOrNull(latIdx)?.toDoubleOrNull()
                                val lon = tokens.getOrNull(lonIdx)?.toDoubleOrNull()
                                val sequence = tokens.getOrNull(sequenceIdx)?.toIntOrNull()

                                // If all parts are valid, create LatLng and add to list with its sequence.
                                if (lat != null && lon != null && sequence != null) {
                                    pointsForShape.add(Pair(sequence, LatLng(lat, lon)))
                                }
                            }
                        } catch (e: Exception) {
                            // Log sparingly for individual line errors during on-demand parsing,
                            // as shapes.txt can be very large and noisy logs are unhelpful.
                            // A general error is posted if major issues occur.
                            // Log.w(TAG, "readShapePointsFromFile: Skipping malformed line in ${shapesFile.name} for shape '$shapeIdToFind': $line", e)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "readShapePointsFromFile: IOException reading ${shapesFile.name} for shape '$shapeIdToFind'", e)
                _error.postValue("Error reading shapes data file.")
                return@withContext emptyList()
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "readShapePointsFromFile: OutOfMemoryError reading ${shapesFile.name} for shape '$shapeIdToFind'", oom)
                _error.postValue("Out of memory processing shape data.")
                return@withContext emptyList()
            }

            if (pointsForShape.isEmpty()) {
                Log.w(TAG, "readShapePointsFromFile: No points found for shape_id '$shapeIdToFind' in ${shapesFile.name}.")
            } else {
                Log.d(TAG, "readShapePointsFromFile: Found ${pointsForShape.size} points for shape_id '$shapeIdToFind'.")
            }
            // Sort the collected points by their sequence number and then map to a list of LatLng objects.
            pointsForShape.sortedBy { it.first }.map { it.second }
        }
    }

    /**
     * Fetches and posts the geographical shape (list of LatLng points) for a given trip ID.
     * It finds the trip, gets its shape_id, and then calls [readShapePointsFromFile]
     * to parse `shapes.txt` on demand for that specific shape.
     * Updates `_isLoadingShape`, `_selectedTripShapePoints`, and `_detailError` LiveData.
     * @param tripId The ID of the trip for which to fetch the shape.
     */
    fun getShapeForTrip(tripId: String?) {
        _isLoadingShape.value = true // Indicate that shape loading has started.
        _selectedTripShapePoints.value = null // Clear previous shape points.
        _detailError.value = null // Clear previous detail errors.

        if (tripId.isNullOrEmpty()) {
            Log.w(TAG, "getShapeForTrip called with null or empty tripId.")
            _selectedTripShapePoints.postValue(emptyList()) // Post empty list if tripId is invalid.
            _isLoadingShape.postValue(false)
            return
        }

        // Launch on Default dispatcher for potentially CPU-bound find operation and IO-bound file reading.
        viewModelScope.launch(Dispatchers.Default) {
            if (allTrips.isEmpty()) {
                Log.w(TAG, "getShapeForTrip: Cannot get shape because Trips data is not loaded.")
                _detailError.postValue("Trip data not available to find shape.")
                _selectedTripShapePoints.postValue(emptyList())
                _isLoadingShape.postValue(false)
                return@launch
            }

            // Find the trip object by its ID.
            val trip = allTrips.find { it.tripId == tripId }
            if (trip?.shapeId != null) { // If trip is found and has a shape_id.
                val gtfsDir = getApplication<Application>().cacheDir
                val unzippedFilesDir = File(gtfsDir, GTFS_UNZIPPED_DIR_NAME)
                val shapesFile = File(unzippedFilesDir, FILE_SHAPES) // Path to shapes.txt.

                if (!shapesFile.exists()){ // Check if shapes.txt exists.
                    Log.e(TAG, "getShapeForTrip: ${FILE_SHAPES} does not exist. Cannot load shape for trip $tripId.")
                    _detailError.postValue("Shapes data file (${FILE_SHAPES}) not found.")
                    _selectedTripShapePoints.postValue(emptyList())
                    _isLoadingShape.postValue(false)
                    return@launch
                }
                // Read shape points from shapes.txt for the trip's shape_id.
                val shapePoints = readShapePointsFromFile(trip.shapeId!!, shapesFile)
                _selectedTripShapePoints.postValue(shapePoints) // Post the loaded shape points.
            } else {
                // If trip is not found or has no shape_id.
                Log.w(TAG, "getShapeForTrip: Trip $tripId has no shape_id or trip itself not found.")
                _selectedTripShapePoints.postValue(emptyList()) // Post empty list as there's no shape.
            }
            _isLoadingShape.postValue(false) // Indicate shape loading is finished.
        }
    }

    /**
     * Clears the LiveData for selected trip shape points.
     * Useful when a selection is cleared or a new selection process begins.
     */
    fun clearSelectedTripShapePoints() {
        _selectedTripShapePoints.postValue(null)
    }


    /**
     * Checks if a given GTFS service (identified by `serviceId`) is active on a specific `date`.
     * It considers both the regular schedule from `calendar.txt` and exceptions from `calendar_dates.txt`.
     *
     * @param serviceId The service_id to check.
     * @param date The [LocalDate] to check service availability for.
     * @return True if the service is active on the given date, false otherwise.
     * Requires API level 26 (Android Oreo) for [LocalDate] and [DateTimeFormatter].
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun isServiceActive(serviceId: String, date: LocalDate): Boolean {
        // First, check for specific exceptions (additions or removals) for this service_id on this date.
        calendarDateExceptionsByServiceId[serviceId]?.forEach { exception ->
            try {
                val exceptionDate = LocalDate.parse(exception.date, GTFS_DATE_FORMATTER)
                if (exceptionDate == date) {
                    // If an exception matches the date:
                    // - Type 1 means service is ADDED, so it's active.
                    // - Type 2 means service is REMOVED, so it's not active.
                    return exception.exceptionType == 1 // Active if type is 1 (added).
                }
            } catch (e: DateTimeParseException) {
                Log.w(TAG, "isServiceActive: Bad date format in calendar_dates.txt for service '$serviceId', date '${exception.date}'", e)
            }
        }

        // If no date-specific exception applies, check the regular calendar pattern.
        val calendarPattern = calendarItems.find { it.serviceId == serviceId }
            ?: return false // If no calendar pattern found for this service_id, it's not active.

        try {
            val startDate = LocalDate.parse(calendarPattern.startDate, GTFS_DATE_FORMATTER)
            val endDate = LocalDate.parse(calendarPattern.endDate, GTFS_DATE_FORMATTER)

            // Check if the given date is within the service pattern's start and end dates.
            if (date.isBefore(startDate) || date.isAfter(endDate)) {
                return false // Not active if outside the date range.
            }

            // Check if the service runs on the specific day of the week.
            return when (date.dayOfWeek) {
                DayOfWeek.MONDAY    -> calendarPattern.monday
                DayOfWeek.TUESDAY   -> calendarPattern.tuesday
                DayOfWeek.WEDNESDAY -> calendarPattern.wednesday
                DayOfWeek.THURSDAY  -> calendarPattern.thursday
                DayOfWeek.FRIDAY    -> calendarPattern.friday
                DayOfWeek.SATURDAY  -> calendarPattern.saturday
                DayOfWeek.SUNDAY    -> calendarPattern.sunday
                else                -> false // Should not happen for standard DayOfWeek values.
            }
        } catch (e: DateTimeParseException) {
            Log.w(TAG, "isServiceActive: Bad date format in calendar.txt for service '$serviceId', start '${calendarPattern.startDate}' or end '${calendarPattern.endDate}'", e)
            return false // Error parsing dates from calendar.txt, assume not active.
        }
    }

    /**
     * Parses `calendar.txt` file to get regular service patterns.
     * @param calendarFile The [File] object for `calendar.txt`.
     * @return A list of [CalendarItem] objects.
     */
    private suspend fun parseCalendarTxt(calendarFile: File): List<CalendarItem> {
        return withContext(Dispatchers.IO) { // Perform file I/O on IO dispatcher.
            val parsedCalendarItems = mutableListOf<CalendarItem>()
            Log.d(TAG, "parseCalendarTxt: Parsing ${calendarFile.name}...")
            if (!calendarFile.exists() || !calendarFile.canRead()) {
                Log.w(TAG, "parseCalendarTxt: ${calendarFile.name} does not exist or cannot be read. Service availability might be affected.")
                _error.postValue("${calendarFile.name} is missing or unreadable.")
                return@withContext parsedCalendarItems // Return empty list if file issue.
            }
            try {
                BufferedReader(FileReader(calendarFile)).use { reader ->
                    val headerLine = reader.readLine() // Read header line.
                    if (headerLine == null) {
                        Log.e(TAG, "parseCalendarTxt: ${calendarFile.name} is empty.")
                        _error.postValue("${calendarFile.name} is empty.")
                        return@use parsedCalendarItems
                    }
                    val headers = headerLine.split(",").map { it.trim() }
                    val columnMap = headers.mapIndexed { index, header -> header to index }.toMap()

                    // Get indices for all required columns.
                    val serviceIdIdx = columnMap["service_id"]
                    val mondayIdx = columnMap["monday"]; val tuesdayIdx = columnMap["tuesday"]
                    val wednesdayIdx = columnMap["wednesday"]; val thursdayIdx = columnMap["thursday"]
                    val fridayIdx = columnMap["friday"]; val saturdayIdx = columnMap["saturday"]
                    val sundayIdx = columnMap["sunday"]; val startDateIdx = columnMap["start_date"]
                    val endDateIdx = columnMap["end_date"]

                    // Validate that all essential columns are present in the header.
                    if (listOf(serviceIdIdx, mondayIdx, tuesdayIdx, wednesdayIdx, thursdayIdx, fridayIdx, saturdayIdx, sundayIdx, startDateIdx, endDateIdx).any { it == null }) {
                        Log.e(TAG, "parseCalendarTxt: Essential columns missing in ${calendarFile.name} header: $headerLine")
                        _error.postValue("Calendar file (${calendarFile.name}) format error: missing essential columns.")
                        return@use parsedCalendarItems
                    }
                    var dataLine: String?
                    // Read each data line.
                    while (reader.readLine().also { dataLine = it } != null) {
                        if (dataLine.isNullOrBlank()) continue // Skip empty lines.
                        val tokens = dataLine!!.split(",").map { it.trim().removeSurrounding("\"") }
                        try {
                            val serviceId = tokens[serviceIdIdx!!]
                            if (serviceId.isNotEmpty()) { // Ensure service_id is not empty.
                                // Create CalendarItem and add to list. Convert "1"/"0" to Boolean.
                                parsedCalendarItems.add(CalendarItem(
                                    serviceId = serviceId,
                                    monday = tokens[mondayIdx!!] == "1",
                                    tuesday = tokens[tuesdayIdx!!] == "1",
                                    wednesday = tokens[wednesdayIdx!!] == "1",
                                    thursday = tokens[thursdayIdx!!] == "1",
                                    friday = tokens[fridayIdx!!] == "1",
                                    saturday = tokens[saturdayIdx!!] == "1",
                                    sunday = tokens[sundayIdx!!] == "1",
                                    startDate = tokens[startDateIdx!!],
                                    endDate = tokens[endDateIdx!!]
                                ))
                            }
                        } catch (e: Exception) { // Catch errors for individual malformed lines.
                            Log.w(TAG, "parseCalendarTxt: Skipping malformed line in ${calendarFile.name}: $dataLine", e)
                        }
                    }
                }
            } catch (e: IOException) { // Catch general I/O errors.
                Log.e(TAG, "parseCalendarTxt: Error parsing ${calendarFile.name}", e)
                _error.postValue("Error reading ${calendarFile.name}: ${e.localizedMessage}")
            }
            Log.d(TAG, "parseCalendarTxt: Finished parsing ${calendarFile.name}. Found ${parsedCalendarItems.size} items.")
            parsedCalendarItems
        }
    }

    /**
     * Parses `calendar_dates.txt` file to get service exceptions (additions/removals).
     * @param calendarDatesFile The [File] object for `calendar_dates.txt`.
     * @return A map where the key is `service_id` and the value is a list of [CalendarDateItem] exceptions for that service.
     */
    private suspend fun parseCalendarDatesTxt(calendarDatesFile: File): Map<String, MutableList<CalendarDateItem>> {
        return withContext(Dispatchers.IO) { // Perform file I/O on IO dispatcher.
            val exceptionsByServiceId = mutableMapOf<String, MutableList<CalendarDateItem>>()
            Log.d(TAG, "parseCalendarDatesTxt: Parsing ${calendarDatesFile.name}...")
            if (!calendarDatesFile.exists() || !calendarDatesFile.canRead()) {
                Log.w(TAG, "parseCalendarDatesTxt: ${calendarDatesFile.name} does not exist or cannot be read. This file is often optional in GTFS.")
                // Not posting an error as this file can be optional.
                return@withContext exceptionsByServiceId
            }
            try {
                BufferedReader(FileReader(calendarDatesFile)).use { reader ->
                    val headerLine = reader.readLine()
                    if (headerLine == null) { // File might be empty, which is valid.
                        Log.d(TAG, "parseCalendarDatesTxt: ${calendarDatesFile.name} is empty.")
                        return@use exceptionsByServiceId
                    }
                    val headers = headerLine.split(",").map { it.trim() }
                    val columnMap = headers.mapIndexed { index, header -> header to index }.toMap()

                    // Get indices for required columns.
                    val serviceIdIdx = columnMap["service_id"]
                    val dateIdx = columnMap["date"]
                    val exceptionTypeIdx = columnMap["exception_type"]

                    // Validate header.
                    if (serviceIdIdx == null || dateIdx == null || exceptionTypeIdx == null) {
                        Log.e(TAG, "parseCalendarDatesTxt: Essential columns missing in ${calendarDatesFile.name} header: $headerLine")
                        _error.postValue("Calendar dates file (${calendarDatesFile.name}) format error: missing essential columns.")
                        return@use exceptionsByServiceId
                    }

                    var dataLine: String?
                    while (reader.readLine().also { dataLine = it } != null) {
                        if (dataLine.isNullOrBlank()) continue
                        val tokens = dataLine!!.split(",").map { it.trim().removeSurrounding("\"") }
                        try {
                            val serviceId = tokens[serviceIdIdx]
                            val date = tokens[dateIdx]
                            val exceptionType = tokens[exceptionTypeIdx].toIntOrNull() // Convert to Int.
                            // Ensure all parts are valid before creating the item.
                            if (serviceId.isNotEmpty() && date.isNotEmpty() && exceptionType != null) {
                                val item = CalendarDateItem(serviceId, date, exceptionType)
                                // Add the exception item to the list for its service_id.
                                exceptionsByServiceId.getOrPut(serviceId) { mutableListOf() }.add(item)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "parseCalendarDatesTxt: Skipping malformed line in ${calendarDatesFile.name}: $dataLine", e)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "parseCalendarDatesTxt: Error parsing ${calendarDatesFile.name}", e)
                _error.postValue("Error reading ${calendarDatesFile.name}: ${e.localizedMessage}")
            }
            Log.d(TAG, "parseCalendarDatesTxt: Finished parsing ${calendarDatesFile.name}. Found exceptions for ${exceptionsByServiceId.size} service IDs.")
            exceptionsByServiceId
        }
    }


    /**
     * Filters the list of all routes based on a search query.
     * Updates `_filteredRoutes` LiveData.
     * @param query The search query string. Search is case-insensitive and checks route short/long names.
     */
    fun searchRoutes(query: String?) {
        val currentQuery = query?.trim()?.lowercase(Locale.getDefault()) ?: ""
        if (currentQuery.isEmpty()) {
            // If query is empty, show all de-duplicated routes.
            _filteredRoutes.value = allDeDuplicatedRoutes
        } else {
            // Filter routes where short name OR long name contains the query.
            val filtered = allDeDuplicatedRoutes.filter { route ->
                val shortNameMatch = route.routeShortName?.lowercase(Locale.getDefault())?.contains(currentQuery) == true
                val longNameMatch = route.routeLongName?.lowercase(Locale.getDefault())?.contains(currentQuery) == true
                shortNameMatch || longNameMatch
            }
            _filteredRoutes.value = filtered
        }
    }

    /**
     * Fetches the sequence of stops for a given route ID.
     * It finds a representative trip for the route and then extracts its stop sequence.
     * Updates `_isLoadingDetails`, `_selectedRouteStopSequence`, and `_detailError` LiveData.
     * @param routeId The ID of the route for which to get the stop sequence.
     */
    fun getStopSequenceForRoute(routeId: String) {
        _isLoadingDetails.value = true // Indicate detail loading started.
        _detailError.value = null      // Clear previous detail errors.
        _selectedRouteStopSequence.value = null // Clear previous stop sequence.

        // Launch on Default dispatcher as it involves searching through lists.
        viewModelScope.launch(Dispatchers.Default) {
            // Check if necessary data collections are loaded.
            if (allTrips.isEmpty() || stopTimesByTripId.isEmpty() || stopsById.isEmpty()) {
                _detailError.postValue("Required schedule data not fully loaded to get stop sequence.")
                _isLoadingDetails.postValue(false)
                return@launch
            }

            // Find a representative trip for the given routeId.
            // Any trip belonging to the route can represent its general stop sequence.
            val representativeTrip = allTrips.find { it.routeId == routeId }
            if (representativeTrip == null) {
                _detailError.postValue("No trip data found for this route ID: $routeId.")
                _selectedRouteStopSequence.postValue(emptyList()) // Post empty list if no trip.
                _isLoadingDetails.postValue(false)
                return@launch
            }

            // Get stop times for the representative trip. These are already sorted by stop_sequence.
            val stopTimesForTrip = stopTimesByTripId[representativeTrip.tripId]
            if (stopTimesForTrip.isNullOrEmpty()) {
                _detailError.postValue("No stop schedule found for a representative trip of route ID: $routeId.")
                _selectedRouteStopSequence.postValue(emptyList())
                _isLoadingDetails.postValue(false)
                return@launch
            }

            // Map StopTime entries to actual Stop objects.
            // If a stop_id from stop_times.txt isn't found in stopsById (data inconsistency),
            // create a placeholder Stop object.
            val stopSequence = stopTimesForTrip.mapNotNull { stopTime ->
                stopsById[stopTime.stopId] ?: Stop(
                    stopId = stopTime.stopId,
                    stopName = "Unknown Stop (ID: ${stopTime.stopId})", // Placeholder name.
                    stopCode = null, stopDesc = null, stopLat = null, stopLon = null,
                    locationType = null, parentStation = null
                )
            }
            _selectedRouteStopSequence.postValue(stopSequence) // Post the resulting stop sequence.
            _isLoadingDetails.postValue(false) // Indicate detail loading finished.
        }
    }

    /**
     * Calculates the distance in meters between two geographical coordinates using the Haversine formula.
     * @param lat1 Latitude of the first point.
     * @param lon1 Longitude of the first point.
     * @param lat2 Latitude of the second point.
     * @param lon2 Longitude of the second point.
     * @return The distance in meters.
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c // Multiply by Earth's radius to get distance in meters.
    }

    /**
     * Finds all stops within a given radius (in meters) from a central [LatLng] location.
     * @param location The central [LatLng] point.
     * @param radiusInMeters The search radius in meters.
     * @return A list of [Stop] objects found within the radius.
     */
    private fun findStopsNearLocation(location: LatLng, radiusInMeters: Double): List<Stop> {
        if (stopsById.isEmpty()) return emptyList() // Return empty if no stops are loaded.
        // Iterate through all loaded stops.
        return stopsById.values.mapNotNull { stop ->
            // Get stop coordinates, parse to Double.
            val stopLat = stop.stopLat?.toDoubleOrNull()
            val stopLon = stop.stopLon?.toDoubleOrNull()
            if (stopLat != null && stopLon != null) {
                // If coordinates are valid, calculate distance.
                if (calculateDistance(location.latitude, location.longitude, stopLat, stopLon) <= radiusInMeters) {
                    stop // Include stop if within radius.
                } else {
                    null // Exclude stop if outside radius.
                }
            } else {
                null // Exclude stop if coordinates are invalid.
            }
        }.also { // Log how many nearby stops were found.
            Log.d(TAG, "findStopsNearLocation: Found ${it.size} stops near ${location.latitude},${location.longitude} within $radiusInMeters meters.")
        }
    }

    /**
     * Parses a GTFS time string (HH:MM:SS) into total seconds from midnight.
     * Handles times > 24:00:00 correctly.
     * @param gtfsTime The GTFS time string.
     * @return The time in total seconds from midnight, or null if parsing fails.
     */
    private fun parseGtfsTimeToSeconds(gtfsTime: String?): Int? {
        if (gtfsTime.isNullOrBlank()) return null
        return try {
            val parts = gtfsTime.split(":")
            val hours = parts[0].toInt()
            val minutes = parts[1].toInt()
            val seconds = parts[2].toInt()
            hours * 3600 + minutes * 60 + seconds // Calculate total seconds.
        } catch (e: Exception) { // Catch NumberFormatException or IndexOutOfBoundsException.
            Log.w(TAG, "parseGtfsTimeToSeconds: Failed to parse GTFS time string '$gtfsTime'", e)
            null
        }
    }

    /**
     * Suggests direct public transport routes between an origin and a destination LatLng.
     * It finds nearby stops, iterates through active trips, and identifies trips that connect
     * a nearby origin stop to a nearby destination stop with a departure time after the current time.
     * The suggestions are prioritized by the earliest departure time for each unique route path.
     *
     * @param originLatLng The [LatLng] of the origin.
     * @param destinationLatLng The [LatLng] of the destination.
     * Requires API level 26 (Android Oreo) for [LocalDate], [LocalTime].
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun suggestDirectRoutes(originLatLng: LatLng, destinationLatLng: LatLng) {
        _isLoadingSuggestions.value = true // Indicate suggestion process started.
        _suggestionError.value = null      // Clear previous suggestion errors.
        _suggestedRouteOptions.value = null // Clear previous suggestions.

        viewModelScope.launch(Dispatchers.Default) { // Use Default dispatcher for CPU-intensive logic.
            // Check if all necessary GTFS data collections are loaded.
            if (allTrips.isEmpty() || stopTimesByTripId.isEmpty() || stopsById.isEmpty() ||
                allDeDuplicatedRoutes.isEmpty() || calendarItems.isEmpty()) {
                _suggestionError.postValue("Core schedule data not fully loaded to suggest routes.")
                _isLoadingSuggestions.postValue(false)
                return@launch
            }

            val currentDate = LocalDate.now() // Get current date for service activity check.
            val currentTimeInSeconds = LocalTime.now().toSecondOfDay() // Get current time in seconds for departure check.
            Log.d(TAG, "suggestDirectRoutes: Suggesting routes for date: $currentDate, current time (seconds): $currentTimeInSeconds")

            // Find stops near the origin and destination.
            val nearbyOriginStops = findStopsNearLocation(originLatLng, NEARBY_STOP_RADIUS_METERS)
            val nearbyDestinationStops = findStopsNearLocation(destinationLatLng, NEARBY_STOP_RADIUS_METERS)

            if (nearbyOriginStops.isEmpty() || nearbyDestinationStops.isEmpty()) {
                val errorMsg = when {
                    nearbyOriginStops.isEmpty() && nearbyDestinationStops.isEmpty() -> "No stops found near origin or destination."
                    nearbyOriginStops.isEmpty() -> "No stops found near your origin."
                    else -> "No stops found near your destination."
                }
                _suggestionError.postValue(errorMsg)
                _suggestedRouteOptions.postValue(emptyList()) // Post empty list.
                _isLoadingSuggestions.postValue(false)
                return@launch
            }

            // Store potential suggestions. Key: "routeId-originStopId-destStopId" to get the best trip per unique path.
            val potentialSuggestions = mutableMapOf<String, SuggestedRouteOption>()
            // Create a map of routes by routeId for quick lookup.
            val routesMap = allDeDuplicatedRoutes.associateBy { it.routeId }

            // Iterate through all loaded trips.
            for (trip in allTrips) {
                yield() // Allow coroutine to yield if processing many trips.
                // Check if the trip's service is active on the current date.
                if (!isServiceActive(trip.serviceId, currentDate)) continue

                val stopTimesForThisTrip = stopTimesByTripId[trip.tripId] ?: continue // Get stop times for this trip.
                var foundOriginStopTime: StopTime? = null

                // Find a suitable origin stop for this trip.
                for (st in stopTimesForThisTrip) {
                    if (nearbyOriginStops.any { it.stopId == st.stopId }) { // If this stop time is for a nearby origin stop.
                        val departureSeconds = parseGtfsTimeToSeconds(st.departureTime ?: st.arrivalTime)
                        // Check if the departure time is in the future (relative to current time of day).
                        // This simple check assumes GTFS times are for the current service day.
                        // More complex logic would be needed for services that span multiple calendar days
                        // with times > 24:00:00 if comparing against absolute timestamps.
                        if (departureSeconds != null && departureSeconds >= currentTimeInSeconds) {
                            foundOriginStopTime = st // Found a valid upcoming origin stop time.
                            break
                        }
                    }
                }
                if (foundOriginStopTime == null) continue // No suitable origin stop found for this trip at a future time.

                var foundDestinationStopTime: StopTime? = null
                // Search for a destination stop *after* the found origin stop in the trip's sequence.
                val originStopTimeIndex = stopTimesForThisTrip.indexOf(foundOriginStopTime)
                for (stIndex in (originStopTimeIndex + 1) until stopTimesForThisTrip.size) {
                    val potentialDestStopTime = stopTimesForThisTrip[stIndex]
                    if (nearbyDestinationStops.any { it.stopId == potentialDestStopTime.stopId }) { // If this stop time is for a nearby dest stop.
                        foundDestinationStopTime = potentialDestStopTime // Found a valid destination stop time.
                        break
                    }
                }

                if (foundDestinationStopTime != null) { // If both origin and destination stops are found on this trip in sequence.
                    val routeDetails = routesMap[trip.routeId]
                    val originStopDetails = stopsById[foundOriginStopTime.stopId]
                    val destinationStopDetails = stopsById[foundDestinationStopTime.stopId]

                    // Ensure all details are available.
                    if (routeDetails != null && originStopDetails != null && destinationStopDetails != null) {
                        val suggestionKey = "${routeDetails.routeId}-${originStopDetails.stopId}-${destinationStopDetails.stopId}"
                        val currentDepartureTime = foundOriginStopTime.departureTime ?: foundOriginStopTime.arrivalTime // Use departure, fallback to arrival.
                        val currentArrivalTime = foundDestinationStopTime.arrivalTime ?: foundDestinationStopTime.departureTime // Use arrival, fallback to departure.

                        // Check if this trip offers an earlier departure for this specific route-origin-destination combination.
                        val existingSuggestion = potentialSuggestions[suggestionKey]
                        if (existingSuggestion == null ||
                            (parseGtfsTimeToSeconds(currentDepartureTime) ?: Int.MAX_VALUE) < (parseGtfsTimeToSeconds(existingSuggestion.originDepartureTime) ?: Int.MAX_VALUE)) {
                            // If no existing suggestion for this path, or this one is earlier, add/update it.
                            potentialSuggestions[suggestionKey] = SuggestedRouteOption(
                                route = routeDetails,
                                originStop = originStopDetails,
                                destinationStop = destinationStopDetails,
                                tripId = trip.tripId, // Include tripId for fetching shape later.
                                originDepartureTime = currentDepartureTime,
                                destinationArrivalTime = currentArrivalTime
                            )
                        }
                    }
                }
            }
            // Sort the collected unique suggestions by their origin departure time.
            val finalSuggestions = potentialSuggestions.values.sortedBy { parseGtfsTimeToSeconds(it.originDepartureTime) ?: Int.MAX_VALUE }

            if (finalSuggestions.isEmpty()) {
                _suggestionError.postValue("No direct upcoming routes found between the selected locations.")
            }
            _suggestedRouteOptions.postValue(finalSuggestions) // Post the final list of suggestions.
            _isLoadingSuggestions.postValue(false) // Indicate suggestion process finished.
        }
    }

    /**
     * Parses `routes.txt` to get a list of [GtfsRoute] objects.
     * It de-duplicates routes based on a composite key of short name, long name, agency ID, and route type
     * to show a cleaner list to the user, as GTFS might have multiple route_ids for variations
     * that appear as the same service to passengers.
     * @param routesFile The [File] object for `routes.txt`.
     * @return A de-duplicated list of [GtfsRoute] objects.
     */
    private suspend fun parseRoutesTxt(routesFile: File): List<GtfsRoute> {
        return withContext(Dispatchers.IO) { // Perform file I/O on IO dispatcher.
            // Use a map with a composite key for de-duplication.
            // The first route_id encountered for a unique combination is kept.
            val distinctRoutesMap = mutableMapOf<String, GtfsRoute>()
            if (!routesFile.exists() || !routesFile.canRead()) {
                _error.postValue("${routesFile.name} is missing or unreadable.")
                return@withContext emptyList<GtfsRoute>()
            }
            try {
                BufferedReader(FileReader(routesFile)).use { reader ->
                    val headerLine = reader.readLine()
                    if (headerLine == null) { _error.postValue("${routesFile.name} is empty."); return@use emptyList<GtfsRoute>() }
                    val headers = headerLine.split(",").map { it.trim() }
                    val columnMap = headers.mapIndexed { index, header -> header to index }.toMap()

                    // Get indices of required and optional columns.
                    val routeIdIdx = columnMap["route_id"] ?: run { _error.postValue("'route_id' missing in ${routesFile.name}."); return@use emptyList<GtfsRoute>() }
                    val agencyIdIdx = columnMap["agency_id"]
                    val routeShortNameIdx = columnMap["route_short_name"]
                    val routeLongNameIdx = columnMap["route_long_name"]
                    val routeTypeIdx = columnMap["route_type"]

                    var dataLine: String?
                    while (reader.readLine().also { dataLine = it } != null) {
                        if (dataLine.isNullOrBlank()) continue
                        // Split line, trim values, and remove surrounding quotes.
                        val tokens = dataLine!!.split(",").map { it.trim().removeSurrounding("\"") }
                        try {
                            val routeId = tokens[routeIdIdx]
                            // Provide empty string as fallback for optional fields if index is null or token missing, then convert to null if empty.
                            val agencyId = agencyIdIdx?.let { tokens.getOrNull(it) } ?: ""
                            val shortName = routeShortNameIdx?.let { tokens.getOrNull(it) } ?: ""
                            val longName = routeLongNameIdx?.let { tokens.getOrNull(it) } ?: ""
                            val routeType = routeTypeIdx?.let { tokens.getOrNull(it) } ?: ""

                            if (routeId.isNotEmpty()) { // Route ID is mandatory.
                                // Create a de-duplication key from user-facing identifiers.
                                val deDuplicationKey = "$shortName|$longName|$agencyId|$routeType"
                                if (!distinctRoutesMap.containsKey(deDuplicationKey)) {
                                    // If this unique combination is not yet in the map, add it.
                                    distinctRoutesMap[deDuplicationKey] = GtfsRoute(
                                        routeId = routeId, // Use the first route_id encountered for this key.
                                        agencyId = if (agencyId.isEmpty()) null else agencyId,
                                        routeShortName = if (shortName.isEmpty()) null else shortName,
                                        routeLongName = if (longName.isEmpty()) null else longName,
                                        routeType = if (routeType.isEmpty()) null else routeType
                                    )
                                }
                            }
                        } catch (e: Exception) { // Catch issues with individual lines.
                            Log.w(TAG, "parseRoutesTxt: Skipping malformed line in ${routesFile.name}: $dataLine", e)
                        }
                    }
                }
            } catch (e: IOException) {
                _error.postValue("Error reading ${routesFile.name}: ${e.localizedMessage}")
            }
            distinctRoutesMap.values.toList() // Return the values (de-duplicated GtfsRoute objects) from the map.
        }
    }

    /**
     * Parses `trips.txt` to get a list of [Trip] objects.
     * @param tripsFile The [File] object for `trips.txt`.
     * @return A list of [Trip] objects.
     */
    private suspend fun parseTripsTxt(tripsFile: File): List<Trip> {
        return withContext(Dispatchers.IO) { // Perform file I/O on IO dispatcher.
            val parsedTrips = mutableListOf<Trip>()
            if (!tripsFile.exists() || !tripsFile.canRead()) {
                _error.postValue("${tripsFile.name} is missing or unreadable.")
                return@withContext parsedTrips
            }
            try {
                BufferedReader(FileReader(tripsFile)).use { reader ->
                    val headerLine = reader.readLine()
                    if (headerLine == null) { _error.postValue("${tripsFile.name} is empty."); return@use parsedTrips }
                    val headers = headerLine.split(",").map { it.trim() }
                    val columnMap = headers.mapIndexed { index, header -> header to index }.toMap()

                    // Get indices for required and optional columns.
                    val routeIdIdx = columnMap["route_id"]
                    val serviceIdIdx = columnMap["service_id"]
                    val tripIdIdx = columnMap["trip_id"]
                    val tripHeadsignIdx = columnMap["trip_headsign"]
                    val directionIdIdx = columnMap["direction_id"]
                    val shapeIdIdx = columnMap["shape_id"]

                    // Validate essential columns.
                    if (routeIdIdx == null || serviceIdIdx == null || tripIdIdx == null) {
                        _error.postValue("Essential columns (route_id, service_id, trip_id) missing in ${tripsFile.name} header.")
                        return@use parsedTrips
                    }

                    var dataLine: String?
                    while (reader.readLine().also { dataLine = it } != null) {
                        if (dataLine.isNullOrBlank()) continue
                        val tokens = dataLine!!.split(",").map { it.trim().removeSurrounding("\"") }
                        try {
                            val routeId = tokens[routeIdIdx]
                            val serviceId = tokens[serviceIdIdx]
                            val tripId = tokens[tripIdIdx]
                            // Handle optional fields, defaulting to null if empty or not present.
                            val tripHeadsign = tripHeadsignIdx?.let { tokens.getOrNull(it) }
                            val directionId = directionIdIdx?.let { tokens.getOrNull(it) }
                            val shapeId = shapeIdIdx?.let { tokens.getOrNull(it) }

                            if (routeId.isNotEmpty() && serviceId.isNotEmpty() && tripId.isNotEmpty()) {
                                parsedTrips.add(Trip(
                                    routeId = routeId,
                                    serviceId = serviceId,
                                    tripId = tripId,
                                    tripHeadsign = if (tripHeadsign.isNullOrEmpty()) null else tripHeadsign,
                                    directionId = if (directionId.isNullOrEmpty()) null else directionId,
                                    shapeId = if (shapeId.isNullOrEmpty()) null else shapeId
                                ))
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "parseTripsTxt: Skipping malformed line in ${tripsFile.name}: $dataLine", e)
                        }
                    }
                }
            } catch (e: IOException) {
                _error.postValue("Error reading ${tripsFile.name}: ${e.localizedMessage}")
            }
            parsedTrips
        }
    }

    /**
     * Parses `stops.txt` to get a map of [Stop] objects, keyed by `stop_id`.
     * @param stopsFile The [File] object for `stops.txt`.
     * @return A map of `stop_id` to [Stop] object.
     */
    private suspend fun parseStopsTxt(stopsFile: File): Map<String, Stop> {
        return withContext(Dispatchers.IO) { // Perform file I/O on IO dispatcher.
            val parsedStops = mutableMapOf<String, Stop>()
            if (!stopsFile.exists() || !stopsFile.canRead()) {
                _error.postValue("${stopsFile.name} is missing or unreadable.")
                return@withContext parsedStops
            }
            try {
                BufferedReader(FileReader(stopsFile)).use { reader ->
                    val headerLine = reader.readLine()
                    if (headerLine == null) { _error.postValue("${stopsFile.name} is empty."); return@use parsedStops }
                    val headers = headerLine.split(",").map { it.trim() }
                    val columnMap = headers.mapIndexed { index, header -> header to index }.toMap()

                    val stopIdIdx = columnMap["stop_id"]
                    val stopCodeIdx = columnMap["stop_code"]
                    val stopNameIdx = columnMap["stop_name"]
                    val stopDescIdx = columnMap["stop_desc"]
                    val stopLatIdx = columnMap["stop_lat"]
                    val stopLonIdx = columnMap["stop_lon"]
                    val locationTypeIdx = columnMap["location_type"]
                    val parentStationIdx = columnMap["parent_station"]

                    if (stopIdIdx == null) { // stop_id is mandatory.
                        _error.postValue("'stop_id' column missing in ${stopsFile.name} header.")
                        return@use parsedStops
                    }

                    var dataLine: String?
                    while (reader.readLine().also { dataLine = it } != null) {
                        if (dataLine.isNullOrBlank()) continue
                        val tokens = dataLine!!.split(",").map { it.trim().removeSurrounding("\"") }
                        try {
                            val stopId = tokens[stopIdIdx]
                            // Handle optional fields, defaulting to null if empty or not present.
                            val stopCode = stopCodeIdx?.let { tokens.getOrNull(it) }
                            val stopName = stopNameIdx?.let { tokens.getOrNull(it) }
                            val stopDesc = stopDescIdx?.let { tokens.getOrNull(it) }
                            val stopLat = stopLatIdx?.let { tokens.getOrNull(it) }
                            val stopLon = stopLonIdx?.let { tokens.getOrNull(it) }
                            val locationType = locationTypeIdx?.let { tokens.getOrNull(it) }
                            val parentStation = parentStationIdx?.let { tokens.getOrNull(it) }

                            if (stopId.isNotEmpty()) {
                                parsedStops[stopId] = Stop(
                                    stopId = stopId,
                                    stopCode = if (stopCode.isNullOrEmpty()) null else stopCode,
                                    stopName = if (stopName.isNullOrEmpty()) "Unnamed Stop (ID: $stopId)" else stopName, // Provide fallback for missing name.
                                    stopDesc = if (stopDesc.isNullOrEmpty()) null else stopDesc,
                                    stopLat = if (stopLat.isNullOrEmpty()) null else stopLat,
                                    stopLon = if (stopLon.isNullOrEmpty()) null else stopLon,
                                    locationType = if (locationType.isNullOrEmpty()) null else locationType,
                                    parentStation = if (parentStation.isNullOrEmpty()) null else parentStation
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "parseStopsTxt: Skipping malformed line in ${stopsFile.name}: $dataLine", e)
                        }
                    }
                }
            } catch (e: IOException) {
                _error.postValue("Error reading ${stopsFile.name}: ${e.localizedMessage}")
            }
            parsedStops
        }
    }

    /**
     * Parses `stop_times.txt` to get a map where the key is `trip_id` and the value is a list
     * of [StopTime] objects for that trip, sorted by `stop_sequence`.
     * @param stopTimesFile The [File] object for `stop_times.txt`.
     * @return A map of `trip_id` to a sorted list of its [StopTime] objects.
     */
    private suspend fun parseStopTimesTxt(stopTimesFile: File): Map<String, List<StopTime>> {
        return withContext(Dispatchers.IO) { // Perform file I/O on IO dispatcher.
            // Temporary map to store stop times, grouped by trip_id.
            val tempStopTimesByTripId = mutableMapOf<String, MutableList<StopTime>>()
            if (!stopTimesFile.exists() || !stopTimesFile.canRead()) {
                _error.postValue("${stopTimesFile.name} is missing or unreadable.")
                return@withContext emptyMap()
            }
            var reader: BufferedReader? = null // Declare reader outside try to close in finally.
            try {
                reader = BufferedReader(FileReader(stopTimesFile))
                val headerLine = reader.readLine()
                if (headerLine == null) { _error.postValue("${stopTimesFile.name} is empty."); return@withContext emptyMap() }
                val headers = headerLine.split(",").map { it.trim() }
                val columnMap = headers.mapIndexed { index, header -> header to index }.toMap()

                // Get indices for essential and optional columns.
                val tripIdIdx = columnMap["trip_id"]
                val arrivalTimeIdx = columnMap["arrival_time"]
                val departureTimeIdx = columnMap["departure_time"]
                val stopIdIdx = columnMap["stop_id"]
                val stopSequenceIdx = columnMap["stop_sequence"]
                val stopHeadsignIdx = columnMap["stop_headsign"]

                // Validate essential columns.
                if (tripIdIdx == null || stopIdIdx == null || stopSequenceIdx == null) {
                    _error.postValue("Essential columns (trip_id, stop_id, stop_sequence) missing in ${stopTimesFile.name} header.")
                    return@withContext emptyMap()
                }

                var dataLine: String?
                while (reader.readLine().also { dataLine = it } != null) {
                    yield() // Yield for very large stop_times files.
                    if (dataLine.isNullOrBlank()) continue
                    // Split line by comma, but handle potential commas within quoted fields carefully if they exist
                    // (though simple split often works if fields aren't complexly quoted).
                    // For robustness, a CSV parsing library would be better for complex GTFS.
                    val tokens = dataLine!!.split(",") // Using simple split.
                    try {
                        // Trim and remove quotes from tokens.
                        val tripId = tokens[tripIdIdx].trim().removeSurrounding("\"")
                        val stopId = tokens[stopIdIdx].trim().removeSurrounding("\"")
                        val stopSequenceStr = tokens[stopSequenceIdx].trim().removeSurrounding("\"")
                        val arrivalTime = arrivalTimeIdx?.let { tokens.getOrNull(it)?.trim()?.removeSurrounding("\"") }
                        val departureTime = departureTimeIdx?.let { tokens.getOrNull(it)?.trim()?.removeSurrounding("\"") }
                        val stopHeadsign = stopHeadsignIdx?.let { tokens.getOrNull(it)?.trim()?.removeSurrounding("\"") }
                        val stopSequence = stopSequenceStr.toIntOrNull() // Convert sequence to Int.

                        if (tripId.isNotEmpty() && stopId.isNotEmpty() && stopSequence != null) {
                            val stopTime = StopTime(
                                tripId = tripId,
                                arrivalTime = if (arrivalTime.isNullOrEmpty()) null else arrivalTime,
                                departureTime = if (departureTime.isNullOrEmpty()) null else departureTime,
                                stopId = stopId,
                                stopSequence = stopSequence,
                                stopHeadsign = if (stopHeadsign.isNullOrEmpty()) null else stopHeadsign
                            )
                            // Add StopTime to the list associated with its trip_id.
                            tempStopTimesByTripId.getOrPut(tripId) { mutableListOf() }.add(stopTime)
                        }
                    } catch (e: Exception) {
                        // Minimized logging for individual line errors in very large files to avoid spamming Logcat.
                        // A general error is posted if the file reading itself fails.
                        // Log.w(TAG, "parseStopTimesTxt: Skipping malformed line in ${stopTimesFile.name}: $dataLine", e)
                    }
                }
            } catch (e: IOException) {
                _error.postValue("Error reading ${stopTimesFile.name}: ${e.localizedMessage}")
            } catch (oom: OutOfMemoryError) { // Catch OOM, as stop_times.txt can be the largest file.
                Log.e(TAG, "parseStopTimesTxt: OutOfMemoryError parsing ${stopTimesFile.name}.", oom)
                _error.postValue("Out of memory processing schedule data (stop_times.txt).")
                tempStopTimesByTripId.clear() // Attempt to free memory.
                return@withContext emptyMap()
            }
            finally {
                // Ensure BufferedReader is closed.
                try { reader?.close() } catch (e: IOException) { Log.e(TAG, "parseStopTimesTxt: Error closing reader for ${stopTimesFile.name}", e) }
            }
            // After parsing all lines, sort the stop times for each trip by their stop_sequence.
            tempStopTimesByTripId.mapValues { entry -> entry.value.sortedBy { it.stopSequence } }
        }
    }

    /**
     * Saves the content of a [ResponseBody] (e.g., downloaded file) to a specified [outputFile].
     * @param body The [ResponseBody] containing the data to save.
     * @param outputFile The [File] to save the data to.
     * @return True if saving was successful, false otherwise.
     */
    private suspend fun saveFile(body: ResponseBody, outputFile: File): Boolean {
        return withContext(Dispatchers.IO) { // Perform file I/O on IO dispatcher.
            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            try {
                inputStream = body.byteStream() // Get input stream from response body.
                outputStream = FileOutputStream(outputFile) // Create output stream to file.
                val buffer = ByteArray(4096) // Buffer for copying data.
                while (true) {
                    val read = inputStream.read(buffer) // Read data into buffer.
                    if (read == -1) break // End of stream.
                    outputStream.write(buffer, 0, read) // Write buffer content to file.
                }
                outputStream.flush() // Ensure all data is written.
                true // Return true on success.
            } catch (e: IOException) {
                Log.e(TAG, "saveFile: Failed to save file to ${outputFile.absolutePath}", e)
                false // Return false on failure.
            } finally {
                // Close streams in finally block to ensure they are closed even if errors occur.
                try { inputStream?.close() } catch (e: IOException) { Log.e(TAG, "saveFile: Error closing input stream", e) }
                try { outputStream?.close() } catch (e: IOException) { Log.e(TAG, "saveFile: Error closing output stream", e) }
            }
        }
    }

    /**
     * Unzips a specified [zipFile] into a [targetDirectory].
     * @param zipFile The .zip file to decompress.
     * @param targetDirectory The directory where the contents will be extracted.
     * @throws IOException if an error occurs during unzipping.
     */
    private suspend fun unzip(zipFile: File, targetDirectory: File) {
        withContext(Dispatchers.IO) { // Perform file I/O on IO dispatcher.
            try {
                // Use ZipInputStream to read the zip file.
                ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
                    var zipEntry: ZipEntry? = zis.nextEntry // Get the next entry in the zip archive.
                    while (zipEntry != null) { // Loop through all entries.
                        val newFile = File(targetDirectory, zipEntry.name) // Create file object for the entry.
                        // Ensure path traversal vulnerability is mitigated if GTFS source is untrusted,
                        // though for a known source like TfWM this is less of a concern.
                        // Example simple check (can be made more robust):
                        // if (!newFile.canonicalPath.startsWith(targetDirectory.canonicalPath)) {
                        //     throw SecurityException("Zip path traversal attempt detected.")
                        // }

                        if (zipEntry.isDirectory) {
                            // If the entry is a directory, create it if it doesn't exist.
                            if (!newFile.isDirectory && !newFile.mkdirs()) {
                                throw IOException("Failed to create directory ${newFile.absolutePath}")
                            }
                        } else {
                            // If the entry is a file, create its parent directories if they don't exist.
                            val parentDir = newFile.parentFile
                            if (parentDir != null && !parentDir.isDirectory && !parentDir.mkdirs()) {
                                throw IOException("Failed to create parent directory ${parentDir.absolutePath}")
                            }
                            // Write the file content.
                            FileOutputStream(newFile).use { fos ->
                                val buffer = ByteArray(1024)
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    fos.write(buffer, 0, len)
                                }
                            }
                        }
                        zis.closeEntry() // Close the current entry.
                        zipEntry = zis.nextEntry // Move to the next entry.
                    }
                }
            } catch (e: IOException) { // Catch errors during unzipping.
                Log.e(TAG, "unzip: Error unzipping file: ${zipFile.absolutePath}", e)
                _error.postValue("Error unzipping GTFS data: ${e.localizedMessage}")
                throw e // Re-throw to be caught by the caller if needed.
            }
        }
    }
}