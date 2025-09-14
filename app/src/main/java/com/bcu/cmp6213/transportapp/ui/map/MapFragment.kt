// File: com/bcu/cmp6213/transportapp/ui/map/MapFragment.kt
package com.bcu.cmp6213.transportapp.ui.map

// Android SDK and AndroidX imports
import android.Manifest // For location permission.
import android.annotation.SuppressLint // To suppress lint warnings, e.g., for fusedLocationClient.lastLocation.
import android.content.pm.PackageManager // For checking permissions.
import android.graphics.Color // For polyline color.
import android.location.Geocoder // For converting addresses to coordinates.
import android.location.Location // For distanceBetween and location objects.
import android.os.Build // To check API levels for conditional code.
import android.os.Bundle
import android.util.Log // For logging.
import androidx.fragment.app.Fragment // Base class for Fragment.
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast // For showing short messages to the user.
import androidx.activity.result.contract.ActivityResultContracts // For the new way of requesting permissions.
import androidx.annotation.RequiresApi // To indicate methods requiring specific API levels.
import androidx.appcompat.app.AlertDialog // For the purchase confirmation dialog.
import androidx.appcompat.widget.SearchView // For the destination search UI.
import androidx.core.content.ContextCompat // For checking permissions.
import androidx.fragment.app.activityViewModels // For sharing ViewModel with the Activity.
import androidx.lifecycle.Observer // To observe LiveData changes.
import androidx.recyclerview.widget.LinearLayoutManager // For RecyclerView layout.

// App-specific imports
import com.bcu.cmp6213.transportapp.R // For accessing resources like layout IDs.
import com.bcu.cmp6213.transportapp.data.SuggestedRouteOption // Data class for route suggestions.
import com.bcu.cmp6213.transportapp.data.Ticket // Data class for purchased tickets.
import com.bcu.cmp6213.transportapp.databinding.FragmentMapBinding // ViewBinding class for fragment_map.xml.
import com.bcu.cmp6213.transportapp.utils.RouteSuggestionAdapter // Adapter for route suggestions RecyclerView.
import com.bcu.cmp6213.transportapp.utils.OnRouteSuggestionClickListener // Interface for suggestion clicks.
import com.bcu.cmp6213.transportapp.viewmodel.GtfsViewModel // Shared ViewModel for GTFS data.

// Google Play Services imports
import com.google.android.gms.location.FusedLocationProviderClient // For getting the device's last known location.
import com.google.android.gms.location.LocationServices // Entry point for Location APIs.
import com.google.android.gms.maps.CameraUpdateFactory // For map camera movements.
import com.google.android.gms.maps.GoogleMap // The main map object.
import com.google.android.gms.maps.OnMapReadyCallback // Callback for when the map is ready.
import com.google.android.gms.maps.SupportMapFragment // Fragment to display the map.
import com.google.android.gms.maps.model.LatLng // Represents a geographical coordinate.
import com.google.android.gms.maps.model.LatLngBounds // For creating bounds to fit map elements.
import com.google.android.gms.maps.model.Marker // For map markers.
import com.google.android.gms.maps.model.MarkerOptions // For configuring markers.
import com.google.android.gms.maps.model.Polyline // For drawing lines on the map.
import com.google.android.gms.maps.model.PolylineOptions // For configuring polylines.

// Firebase imports
import com.google.firebase.Firebase // Base Firebase import.
import com.google.firebase.auth.auth // Firebase Authentication.
import com.google.firebase.firestore.firestore // Firebase Firestore.

// Java utility imports
import java.io.IOException // For handling I/O errors, e.g., with Geocoder.
import java.util.Locale // For Geocoder locale.
import java.util.UUID // For generating unique IDs (e.g., for mock QR codes).
import kotlin.math.max // For segment extraction logic.
import kotlin.math.min // For segment extraction logic.

/**
 * A [Fragment] that displays a Google Map for route planning and suggestion.
 * It allows users to:
 * - See their current location.
 * - Search for a destination.
 * - View suggested public transport routes.
 * - See the selected route's path drawn on the map.
 * - "Buy" a mock ticket for a selected route.
 *
 * Implements [OnMapReadyCallback] to handle map initialization and
 * [OnRouteSuggestionClickListener] to handle clicks on route suggestions.
 */
class MapFragment : Fragment(), OnMapReadyCallback, OnRouteSuggestionClickListener {

    // ViewBinding instance for fragment_map.xml. Nullable, valid between onCreateView and onDestroyView.
    private var _binding: FragmentMapBinding? = null
    // Non-null accessor for binding. Throws an exception if accessed outside the valid lifecycle.
    private val binding get() = _binding!!

    // Google Map instance. Null until the map is ready.
    private var googleMap: GoogleMap? = null
    // Shared ViewModel scoped to the MainActivity for accessing GTFS data and business logic.
    private val gtfsViewModel: GtfsViewModel by activityViewModels()

    // Client for accessing device location.
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    // Markers for current location and destination on the map.
    private var currentLocationMarker: Marker? = null
    private var destinationMarker: Marker? = null
    // LatLng objects to store origin and destination coordinates.
    private var originLatLng: LatLng? = null
    private var destinationLatLng: LatLng? = null

    // Polyline object for the currently displayed route path.
    private var currentRoutePolyline: Polyline? = null
    // Stores the currently selected route suggestion from the list.
    private var currentSelectedSuggestion: SuggestedRouteOption? = null

    // Adapter for the RecyclerView displaying route suggestions.
    private lateinit var routeSuggestionAdapter: RouteSuggestionAdapter

    // Firebase Firestore and Authentication instances.
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    // ActivityResultLauncher for requesting location permission.
    // The @RequiresApi might be overly broad if only specific parts of the lambda require Oreo,
    // but indicates some API level consideration during development.
    @RequiresApi(Build.VERSION_CODES.O)
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Location permission granted by user.")
                getCurrentLocationAndFocus() // Get location if permission granted.
            } else {
                Log.d(TAG, "Location permission denied by user.")
                Toast.makeText(requireContext(), "Location permission denied. Showing default location.", Toast.LENGTH_LONG).show()
                setDefaultMapLocation() // Show default map location if permission denied.
            }
        }

    // Companion object for constants and the factory method.
    companion object {
        private const val TAG = "MapFragment" // TAG for logging.
        private val DEFAULT_LOCATION = LatLng(52.4862, -1.8904) // Default map location (Birmingham).
        private const val DEFAULT_ZOOM = 12f // Default map zoom level.
        private const val CURRENT_LOCATION_ZOOM = 15f // Zoom level for current location.

        /**
         * Factory method to create a new instance of this fragment.
         * @return A new instance of fragment MapFragment.
         */
        @JvmStatic
        fun newInstance() = MapFragment()
    }

    /**
     * Called to have the fragment instantiate its user interface view.
     * Inflates the layout and initializes the FusedLocationProviderClient.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false) // Inflate layout using ViewBinding.
        // Initialize the FusedLocationProviderClient for getting device location.
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        return binding.root // Return the root view of the inflated layout.
    }

    /**
     * Called immediately after onCreateView() has returned, but before any saved state has been restored.
     * Sets up the map, UI elements (RecyclerView, SearchView, buttons), and ViewModel observers.
     */
    @RequiresApi(Build.VERSION_CODES.O) // Potentially due to calls within setup methods or permission launcher usage.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Get the SupportMapFragment and request notification when the map is ready to be used.
        val mapFragment = childFragmentManager.findFragmentById(R.id.map_container) as? SupportMapFragment
        mapFragment?.getMapAsync(this) // 'this' fragment implements OnMapReadyCallback.
            ?: Log.e(TAG, "SupportMapFragment (map_container) not found.") // Log error if map fragment is missing.

        // Setup UI components.
        setupRecyclerViewForSuggestions()
        setupDestinationSearch()
        // Setup ViewModel observers.
        observeViewModelForSuggestions()
        observeViewModelForShapeData()

        // Set OnClickListener for the "Buy Ticket" button located in the selected route info card.
        binding.buttonConfirmBuyTicket.setOnClickListener {
            currentSelectedSuggestion?.let { suggestion ->
                // If a suggestion is selected, show the purchase confirmation dialog.
                showPurchaseConfirmationDialog(suggestion)
            } ?: run {
                // If no suggestion is selected, inform the user.
                Toast.makeText(requireContext(), "No route selected to buy.", Toast.LENGTH_SHORT).show()
            }
        }
        // Set OnClickListener for the FloatingActionButton to get current location.
        // This was missing in the provided latest file but was present in an earlier version of this thought process.
        // Assuming it should be here:
        binding.fabCurrentLocation.setOnClickListener {
            Log.d(TAG, "Current location FAB clicked.")
            requestLocationPermission()
        }
    }

    /**
     * Called when the GoogleMap object is ready to be used.
     * Initializes map settings and requests location permission.
     * @param map The GoogleMap object.
     */
    @RequiresApi(Build.VERSION_CODES.O) // Potentially due to requestLocationPermission call.
    override fun onMapReady(map: GoogleMap) {
        googleMap = map // Store the GoogleMap instance.
        Log.d(TAG, "GoogleMap is ready.")
        googleMap?.uiSettings?.isZoomControlsEnabled = true // Enable zoom controls on the map.
        requestLocationPermission() // Request location permission to show user's location.
    }

    /**
     * Sets up the RecyclerView for displaying route suggestions.
     * Initializes the adapter and LayoutManager.
     */
    private fun setupRecyclerViewForSuggestions() {
        routeSuggestionAdapter = RouteSuggestionAdapter(this) // 'this' fragment implements OnRouteSuggestionClickListener.
        binding.recyclerViewRouteSuggestions.apply {
            layoutManager = LinearLayoutManager(requireContext()) // Use a vertical list layout.
            adapter = routeSuggestionAdapter // Set the adapter.
        }
    }

    /**
     * Requests location permission from the user.
     * Handles different cases: permission already granted, rationale needed, or direct request.
     */
    @RequiresApi(Build.VERSION_CODES.O) // Potentially due to requestPermissionLauncher.launch or shouldShowRequestPermissionRationale.
    private fun requestLocationPermission() {
        when {
            // If permission is already granted, get the current location.
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocationAndFocus()
            }
            // If rationale should be shown, explain why permission is needed.
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Toast.makeText(requireContext(), "Location permission is needed to show your current position.", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) // Launch permission request.
            }
            // Otherwise, directly request the permission.
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    /**
     * Gets the user's current location using FusedLocationProviderClient and focuses the map on it.
     * Requires ACCESS_FINE_LOCATION permission.
     */
    @RequiresApi(Build.VERSION_CODES.O) // Potentially due to checkIfReadyForRouteSuggestion or other calls.
    @SuppressLint("MissingPermission") // Lint check suppressed as permission is checked before calling.
    private fun getCurrentLocationAndFocus() {
        // Double-check permission (though requestLocationPermission should handle this).
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            setDefaultMapLocation(); return
        }
        // Get the last known location.
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                originLatLng = LatLng(location.latitude, location.longitude) // Store as origin.
                Log.d(TAG, "Current location: $originLatLng")
                currentLocationMarker?.remove() // Remove any old current location marker.
                // Add a new marker for the current location.
                currentLocationMarker = googleMap?.addMarker(MarkerOptions().position(originLatLng!!).title("Your Location (Origin)"))
                // Animate camera to the current location.
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(originLatLng!!, CURRENT_LOCATION_ZOOM))
                checkIfReadyForRouteSuggestion() // Check if origin and destination are set to fetch suggestions.
            } else {
                // Handle cases where location is null (e.g., location services off, emulator issues).
                Log.w(TAG, "FusedLocationClient.lastLocation returned null.")
                Toast.makeText(requireContext(), "Could not get current location. Ensure GPS is active and location set in emulator. Showing default.", Toast.LENGTH_LONG).show()
                setDefaultMapLocation()
            }
        }.addOnFailureListener { e ->
            // Handle failure to get location.
            Log.e(TAG, "Error getting current location", e)
            Toast.makeText(requireContext(), "Error obtaining location. Showing default.", Toast.LENGTH_SHORT).show()
            setDefaultMapLocation()
        }
    }

    /**
     * Moves the map camera to a default location (Birmingham) if current location is unavailable.
     */
    private fun setDefaultMapLocation() {
        originLatLng = null // Reset origin if using default location.
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM))
    }

    /**
     * Sets up the SearchView for destination input.
     * Handles query submission to geocode the address.
     */
    private fun setupDestinationSearch() {
        binding.searchViewDestination.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            // Called when the user submits the search query (e.g., presses enter).
            @RequiresApi(Build.VERSION_CODES.O) // Potentially due to geocodeDestination call.
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    geocodeDestination(query) // Convert address to LatLng.
                    binding.searchViewDestination.clearFocus() // Hide keyboard.
                }
                return true // Indicate the query has been handled.
            }
            // Called when the text in the SearchView changes (not used here).
            override fun onQueryTextChange(newText: String?): Boolean { return false }
        })
    }

    /**
     * Converts a destination address (String) into geographical coordinates (LatLng) using Geocoder.
     * Updates the map with a destination marker and checks if route suggestions can be fetched.
     * @param address The destination address string.
     */
    @RequiresApi(Build.VERSION_CODES.O) // Potentially due to checkIfReadyForRouteSuggestion or Geocoder.getFromLocationName API level.
    private fun geocodeDestination(address: String) {
        // Check if Geocoder service is available on the device.
        if (!Geocoder.isPresent()) {
            Toast.makeText(requireContext(), "Geocoder service not available.", Toast.LENGTH_LONG).show(); return
        }
        val geocoder = Geocoder(requireContext(), Locale.getDefault()) // Use default locale.
        try {
            // For Android Tiramisu (API 33) and above, use the new Geocoder API with a callback.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(address, 1) { addresses -> // Max 1 result.
                    activity?.runOnUiThread { // Ensure UI updates are on the main thread.
                        if (addresses.isNotEmpty()) {
                            val loc = addresses[0]
                            destinationLatLng = LatLng(loc.latitude, loc.longitude) // Store destination.
                            destinationMarker?.remove() // Remove old destination marker.
                            // Add new destination marker.
                            destinationMarker = googleMap?.addMarker(MarkerOptions().position(destinationLatLng!!).title("Destination: $address"))
                            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(destinationLatLng!!, CURRENT_LOCATION_ZOOM))
                            checkIfReadyForRouteSuggestion() // Check for route suggestions.
                        } else {
                            Toast.makeText(requireContext(), "Destination not found: $address", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else { // For older Android versions, use the deprecated synchronous Geocoder API.
                @Suppress("DEPRECATION") // Suppress deprecation warning for getFromLocationName.
                val addresses = geocoder.getFromLocationName(address, 1) // Max 1 result.
                if (addresses != null && addresses.isNotEmpty()) {
                    val loc = addresses[0]
                    destinationLatLng = LatLng(loc.latitude, loc.longitude)
                    destinationMarker?.remove()
                    destinationMarker = googleMap?.addMarker(MarkerOptions().position(destinationLatLng!!).title("Destination: $address"))
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(destinationLatLng!!, CURRENT_LOCATION_ZOOM))
                    checkIfReadyForRouteSuggestion()
                } else {
                    Toast.makeText(requireContext(), "Destination not found (legacy): $address", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: IOException) { // Handle network errors during geocoding.
            Toast.makeText(requireContext(), "Network error during geocoding.", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) { // Handle invalid address input.
            Toast.makeText(requireContext(), "Invalid address for geocoding.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Checks if both origin and destination LatLng are set. If so, requests route suggestions.
     * Also clears any previously selected route UI elements (card, polyline, selection).
     */
    @RequiresApi(Build.VERSION_CODES.O) // From GtfsViewModel.suggestDirectRoutes potentially if it has API level constraints.
    private fun checkIfReadyForRouteSuggestion() {
        val currentOrigin = originLatLng
        val currentDestination = destinationLatLng

        // Reset UI elements related to a previously selected route or old suggestions.
        binding.cardViewSelectedRouteInfo.visibility = View.GONE // Hide the selected route info card.
        currentRoutePolyline?.remove() // Remove any existing polyline from the map.
        currentRoutePolyline = null
        currentSelectedSuggestion = null // Clear the stored selected suggestion.

        if (currentOrigin != null && currentDestination != null) {
            // If both origin and destination are set, request route suggestions from the ViewModel.
            Log.d(TAG, "Origin ($currentOrigin) and Destination ($currentDestination) are set. Requesting route suggestions.")
            gtfsViewModel.suggestDirectRoutes(currentOrigin, currentDestination)
        } else {
            // If not ready, log it and clear the suggestions list in the UI.
            Log.d(TAG, "Not ready for route suggestion. Origin: $currentOrigin, Destination: $currentDestination")
            routeSuggestionAdapter.submitList(emptyList())
            binding.recyclerViewRouteSuggestions.visibility = View.GONE
        }
    }

    /**
     * Observes LiveData from [GtfsViewModel] related to route suggestions (loading state, data, errors)
     * and updates the UI accordingly.
     */
    private fun observeViewModelForSuggestions() {
        // Observe loading state for suggestions.
        gtfsViewModel.isLoadingSuggestions.observe(viewLifecycleOwner, Observer { isLoading ->
            binding.progressBarSuggestions.visibility = if (isLoading) View.VISIBLE else View.GONE // Show/hide progress bar.
            if (isLoading) {
                binding.recyclerViewRouteSuggestions.visibility = View.GONE // Hide suggestions list while loading.
            } else {
                updateSuggestionsUIVisibility() // Update UI once loading is done.
            }
        })

        // Observe the list of suggested routes.
        gtfsViewModel.suggestedRouteOptions.observe(viewLifecycleOwner, Observer {
            // Update UI only if not currently loading to avoid premature updates.
            if (gtfsViewModel.isLoadingSuggestions.value == false) {
                updateSuggestionsUIVisibility()
            }
        })

        // Observe errors related to fetching suggestions.
        gtfsViewModel.suggestionError.observe(viewLifecycleOwner, Observer { error ->
            if (gtfsViewModel.isLoadingSuggestions.value == false) {
                updateSuggestionsUIVisibility() // Update UI (likely to show error state).
            }
            if (error != null) { // Show error Toast if an error message is present.
                Toast.makeText(requireContext(), "Route Suggestion: $error", Toast.LENGTH_LONG).show()
            }
        })
    }

    /**
     * Centralized function to update the visibility of UI elements related to route suggestions
     * (RecyclerView for suggestions, selected route info card) based on the current data and error state.
     * Called when suggestion loading finishes or when suggestion/error LiveData changes.
     */
    private fun updateSuggestionsUIVisibility() {
        // Do not update if suggestions are still actively loading.
        if (gtfsViewModel.isLoadingSuggestions.value == true) {
            binding.recyclerViewRouteSuggestions.visibility = View.GONE
            return
        }

        val currentSuggestions = gtfsViewModel.suggestedRouteOptions.value
        val currentError = gtfsViewModel.suggestionError.value

        if (currentError != null) {
            // If there's an error, hide suggestions list and the selected route info card.
            Log.d(TAG, "updateSuggestionsUIVisibility: Error state - $currentError")
            binding.recyclerViewRouteSuggestions.visibility = View.GONE
            routeSuggestionAdapter.submitList(emptyList())
            binding.cardViewSelectedRouteInfo.visibility = View.GONE
        } else if (!currentSuggestions.isNullOrEmpty()) {
            // If suggestions are available, show the suggestions list.
            // The selected route info card is shown only when a specific suggestion is clicked.
            Log.d(TAG, "updateSuggestionsUIVisibility: Displaying ${currentSuggestions.size} suggestions.")
            binding.recyclerViewRouteSuggestions.visibility = View.VISIBLE
            routeSuggestionAdapter.submitList(currentSuggestions)
        } else {
            // No error, but suggestions are null or empty. Hide suggestions list and card.
            Log.d(TAG, "updateSuggestionsUIVisibility: No suggestions or error. Hiding list.")
            binding.recyclerViewRouteSuggestions.visibility = View.GONE
            routeSuggestionAdapter.submitList(emptyList())
            binding.cardViewSelectedRouteInfo.visibility = View.GONE
        }
    }

    /**
     * Finds the index of the point in a list of LatLngs (`pathPoints`)
     * that is geographically closest to a given `targetPoint`.
     * @param pathPoints The list of LatLng points representing the shape path.
     * @param targetPoint The LatLng point (e.g., a stop location) to find the closest point to.
     * @return The index of the closest point in `pathPoints`, or -1 if `pathPoints` is empty.
     */
    private fun findClosestPointIndex(pathPoints: List<LatLng>, targetPoint: LatLng): Int {
        if (pathPoints.isEmpty()) return -1 // Return -1 if the path is empty.
        var closestIndex = 0
        var minDistance = Float.MAX_VALUE // Initialize with a very large distance.

        // Iterate through all points in the path to find the one with the minimum distance.
        for (i in pathPoints.indices) {
            val currentPoint = pathPoints[i]
            val distanceResults = FloatArray(1) // Array to store distance result from Location.distanceBetween.
            // Calculate the distance between the target point and the current path point.
            Location.distanceBetween(
                targetPoint.latitude, targetPoint.longitude,
                currentPoint.latitude, currentPoint.longitude,
                distanceResults
            )
            // If this point is closer than the previous minimum, update minDistance and closestIndex.
            if (distanceResults[0] < minDistance) {
                minDistance = distanceResults[0]
                closestIndex = i
            }
        }
        return closestIndex // Return the index of the closest point found.
    }

    /**
     * Observes LiveData from [GtfsViewModel] related to the selected trip's shape (polyline) data.
     * Handles drawing the route segment on the map.
     */
    private fun observeViewModelForShapeData() {
        // Observe loading state for shape data.
        gtfsViewModel.isLoadingShape.observe(viewLifecycleOwner, Observer { isLoading ->
            if (isLoading) {
                Log.d(TAG, "Shape data is loading...")
                binding.progressBarSuggestions.visibility = View.VISIBLE // Reuse suggestions progress bar for shape loading.
            } else {
                Log.d(TAG, "Shape data loading finished.")
                binding.progressBarSuggestions.visibility = View.GONE
            }
        })

        // Observe the list of LatLng points for the selected trip's shape.
        gtfsViewModel.selectedTripShapePoints.observe(viewLifecycleOwner, Observer { fullShapePath ->
            Log.d(TAG, "selectedTripShapePoints Observer: Received fullShapePath. Size: ${fullShapePath?.size ?: "null"}")
            currentRoutePolyline?.remove() // Remove any previously drawn polyline.
            currentRoutePolyline = null

            val suggestion = currentSelectedSuggestion // Get the currently selected suggestion.
            if (suggestion == null) {
                Log.w(TAG, "selectedTripShapePoints Observer: currentSelectedSuggestion is NULL. Cannot draw segment.")
                return@Observer // Exit if no suggestion is selected.
            }
            Log.d(TAG, "selectedTripShapePoints Observer: Processing shape for suggestion tripId: ${suggestion.tripId}")


            if (fullShapePath.isNullOrEmpty()) {
                // If the full shape path is null or empty after loading has finished.
                if (gtfsViewModel.isLoadingShape.value == false) {
                    Log.w(TAG, "selectedTripShapePoints Observer: fullShapePath is null or empty for tripId ${suggestion.tripId}.")
                    Toast.makeText(requireContext(), "Route path not available for this trip.", Toast.LENGTH_SHORT).show()
                }
                return@Observer // Exit if no shape path data.
            }

            // Get coordinates for the origin and destination stops of the selected suggestion.
            val originStop = suggestion.originStop
            val destStop = suggestion.destinationStop
            val originStopLatLng = originStop.stopLat?.toDoubleOrNull()?.let { lat ->
                originStop.stopLon?.toDoubleOrNull()?.let { lon -> LatLng(lat, lon) }
            }
            val destStopLatLng = destStop.stopLat?.toDoubleOrNull()?.let { lat ->
                destStop.stopLon?.toDoubleOrNull()?.let { lon -> LatLng(lat, lon) }
            }
            Log.d(TAG, "selectedTripShapePoints Observer: OriginLL: $originStopLatLng, DestLL: $destStopLatLng for segment calculation.")


            if (originStopLatLng == null || destStopLatLng == null) {
                // If stop coordinates are missing, cannot draw the specific segment.
                Log.w(TAG, "selectedTripShapePoints Observer: Origin or Destination LatLng is NULL for suggestion. Cannot draw segment.")
                Toast.makeText(requireContext(), "Stop location data missing for path segment.", Toast.LENGTH_SHORT).show()
                // Optionally, could draw the full path as a fallback here if desired.
                return@Observer
            }

            // Find the indices on the full shape path that are closest to the origin and destination stops.
            val originIndexOnShape = findClosestPointIndex(fullShapePath, originStopLatLng)
            val destIndexOnShape = findClosestPointIndex(fullShapePath, destStopLatLng)
            Log.d(TAG, "selectedTripShapePoints Observer: Mapped to shape indices - OriginIndex: $originIndexOnShape, DestIndex: $destIndexOnShape")


            if (originIndexOnShape == -1 || destIndexOnShape == -1) {
                // If stops cannot be mapped to the shape path, draw the full path as a fallback.
                Log.w(TAG, "selectedTripShapePoints Observer: Could not map origin or destination stop to the shape path. Drawing full path as fallback.")
                val polylineOptions = PolylineOptions().addAll(fullShapePath).color(Color.MAGENTA).width(8f) // Use a distinct color for fallback.
                currentRoutePolyline = googleMap?.addPolyline(polylineOptions)
                // Adjust camera to show the full path.
                if (fullShapePath.isNotEmpty()) {
                    val builder = LatLngBounds.Builder()
                    fullShapePath.forEach { builder.include(it) }
                    try {
                        googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100)) // 100px padding.
                    } catch (e: IllegalStateException) { Log.e(TAG, "Error animating to full shape bounds for fallback", e)}
                }
                return@Observer
            }

            // Determine the start and end indices for the sublist to extract the segment.
            // Ensures startIndex is always less than or equal to endIndex.
            val startIndex = min(originIndexOnShape, destIndexOnShape)
            val endIndex = max(originIndexOnShape, destIndexOnShape)
            Log.d(TAG, "selectedTripShapePoints Observer: Calculated segment indices - StartIndex: $startIndex, EndIndex: $endIndex")


            if (startIndex == endIndex) {
                // If origin and destination map to the same point, no segment to draw.
                Log.w(TAG, "selectedTripShapePoints Observer: StartIndex == EndIndex. Stops map to the same point on shape. No segment to draw.")
                return@Observer
            }

            // Extract the segment points from the full shape path.
            val segmentPoints = fullShapePath.subList(startIndex, endIndex + 1) // endIndex + 1 because subList's toIndex is exclusive.
            Log.d(TAG, "selectedTripShapePoints Observer: Extracted segment points count: ${segmentPoints.size}")


            if (segmentPoints.isEmpty()) {
                Log.w(TAG, "selectedTripShapePoints Observer: Extracted segment points list is empty (unexpected). Cannot draw polyline.")
                return@Observer
            }

            // Draw the extracted segment polyline on the map.
            Log.d(TAG, "selectedTripShapePoints Observer: Drawing segment polyline.")
            val polylineOptions = PolylineOptions()
                .addAll(segmentPoints)
                .color(Color.BLUE) // Set color for the segment.
                .width(12f)       // Set width for the segment.
                .zIndex(1f)       // Set zIndex to ensure it's drawn on top if other polylines exist.
            currentRoutePolyline = googleMap?.addPolyline(polylineOptions)

            // Adjust the map camera to fit the drawn segment and the origin/destination markers.
            if (segmentPoints.isNotEmpty()) {
                val builder = LatLngBounds.Builder()
                segmentPoints.forEach { builder.include(it) } // Include all segment points in bounds.
                // Also include the actual stop locations, as they might be slightly off the "closest" shape point.
                builder.include(originStopLatLng)
                builder.include(destStopLatLng)
                try {
                    val cameraUpdate = CameraUpdateFactory.newLatLngBounds(builder.build(), 150) // 150px padding.
                    googleMap?.animateCamera(cameraUpdate)
                } catch (e: IllegalStateException) { // Catch error if bounds cannot be built (e.g., no points).
                    Log.e(TAG, "Cannot animate camera to segment bounds", e)
                }
            }
        })

        // Observe errors specifically related to loading shape/detail data.
        gtfsViewModel.detailError.observe(viewLifecycleOwner, Observer { error ->
            if (error != null && gtfsViewModel.isLoadingShape.value == false) {
                Toast.makeText(requireContext(), "Error loading route path details: $error", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error observed for shape/detail loading: $error")
            }
        })
    }

    /**
     * Callback for when a route suggestion is clicked in the RecyclerView.
     * Implements [OnRouteSuggestionClickListener].
     * Sets the selected suggestion, triggers fetching its shape, and updates UI to show route info card.
     * @param suggestion The [SuggestedRouteOption] that was clicked.
     */
    override fun onSuggestionClick(suggestion: SuggestedRouteOption) {
        Log.d(TAG, "onSuggestionClick: Route - ${suggestion.route.routeShortName ?: suggestion.route.routeLongName}, Trip ID: ${suggestion.tripId}")
        currentSelectedSuggestion = suggestion // Store the clicked suggestion. This is used by shape observer.

        // 1. Immediately request to draw the polyline for the selected suggestion.
        currentRoutePolyline?.remove() // Clear any previous polyline.
        if (suggestion.tripId != null) {
            Log.d(TAG, "onSuggestionClick: Requesting shape for tripId: ${suggestion.tripId}")
            gtfsViewModel.getShapeForTrip(suggestion.tripId) // ViewModel fetches shape points.
        } else {
            // If no tripId, cannot fetch shape. Clear LiveData and polyline object.
            Log.w(TAG, "onSuggestionClick: tripId is null for selected suggestion, cannot get shape.")
            gtfsViewModel.clearSelectedTripShapePoints()
            currentRoutePolyline = null
            Toast.makeText(requireContext(), "Route path details not available for this option.", Toast.LENGTH_SHORT).show()
        }

        // 2. Populate and show the card with details of the selected route.
        populateSelectedRouteCard(suggestion)
        binding.cardViewSelectedRouteInfo.visibility = View.VISIBLE
        // Hide the suggestions list to make space for the selected route info card.
        binding.recyclerViewRouteSuggestions.visibility = View.GONE
    }

    /**
     * Populates the `cardViewSelectedRouteInfo` with details from the given [SuggestedRouteOption].
     * @param suggestion The selected route suggestion.
     */
    private fun populateSelectedRouteCard(suggestion: SuggestedRouteOption) {
        val route = suggestion.route
        // Clean and combine route short/long names for display.
        val cleanedShortName = route.routeShortName?.trim()?.removeSurrounding("\"")
        val cleanedLongName = route.routeLongName?.trim()?.removeSurrounding("\"")
        val routeDisplayName: String = when {
            !cleanedShortName.isNullOrBlank() && !cleanedLongName.isNullOrBlank() -> {
                if (cleanedLongName.contains(cleanedShortName) || cleanedShortName == cleanedLongName) cleanedLongName else "$cleanedShortName - $cleanedLongName"
            }
            !cleanedShortName.isNullOrBlank() -> cleanedShortName
            !cleanedLongName.isNullOrBlank() -> cleanedLongName
            else -> "N/A"
        }
        // Set text for route name, including its type.
        binding.textViewSelectedRouteName.text = "Route: $routeDisplayName (${getRouteTypeName(route.routeType)})"
        // Set text for journey (origin to destination stops).
        binding.textViewSelectedRouteJourney.text = "From: ${suggestion.originStop.stopName ?: "N/A"}\nTo: ${suggestion.destinationStop.stopName ?: "N/A"}"

        // Format and set departure and arrival times.
        val formattedDeparture = formatDisplayTime(suggestion.originDepartureTime)
        val formattedArrival = formatDisplayTime(suggestion.destinationArrivalTime)
        binding.textViewSelectedRouteTimes.text = "Departs: $formattedDeparture, Arrives: $formattedArrival"
    }

    /**
     * Helper function to format GTFS time strings (HH:MM:SS) to a displayable HH:MM format.
     * @param gtfsTime The GTFS time string.
     * @return Formatted time string (HH:MM) or "N/A" if input is invalid.
     */
    private fun formatDisplayTime(gtfsTime: String?): String {
        if (gtfsTime.isNullOrBlank()) return "N/A"
        val parts = gtfsTime.split(":") // Split HH:MM:SS.
        return if (parts.size >= 2) "${parts[0]}:${parts[1]}" else gtfsTime // Return HH:MM or original if not parsable.
    }

    /**
     * Helper function to get a human-readable name for a GTFS route type code.
     * @param routeType The GTFS route type code (String).
     * @return The name of the route type (e.g., "Bus", "Tram") or "Unknown".
     */
    private fun getRouteTypeName(routeType: String?): String {
        // Map GTFS route type codes to display names.
        return when (routeType?.trim()) {
            "0" -> "Tram"; "1" -> "Subway/Metro"; "2" -> "Rail"; "3" -> "Bus"; "4" -> "Ferry"
            "5" -> "Cable Car"; "6" -> "Gondola"; "7" -> "Funicular"; else -> "Unknown"
        }
    }

    /**
     * Displays an AlertDialog to confirm the mock purchase of a ticket.
     * @param suggestion The [SuggestedRouteOption] for which the ticket is being "purchased".
     */
    private fun showPurchaseConfirmationDialog(suggestion: SuggestedRouteOption) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Ticket Purchase")
            .setMessage("Route: ${suggestion.route.routeShortName ?: suggestion.route.routeLongName}\n" +
                    "From: ${suggestion.originStop.stopName}\n" +
                    "To: ${suggestion.destinationStop.stopName}\n\n" +
                    "Do you want to 'buy' this ticket for a mock fare of £2.50?")
            .setPositiveButton("Yes, Buy Ticket") { dialog, _ ->
                purchaseTicket(suggestion) // Proceed with purchase if "Yes".
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss dialog if "Cancel".
            }
            .show()
    }

    /**
     * Handles the mock "purchase" of a ticket.
     * Creates a [Ticket] object and saves it to Firestore.
     * @param suggestion The [SuggestedRouteOption] being "purchased".
     */
    private fun purchaseTicket(suggestion: SuggestedRouteOption) {
        val currentUser = auth.currentUser // Get current Firebase user.
        if (currentUser == null) {
            // User must be logged in to purchase.
            Toast.makeText(requireContext(), "You must be logged in to purchase a ticket.", Toast.LENGTH_LONG).show()
            return
        }

        // Prepare route display name for the ticket.
        val route = suggestion.route
        val cleanedShortName = route.routeShortName?.trim()?.removeSurrounding("\"")
        val cleanedLongName = route.routeLongName?.trim()?.removeSurrounding("\"")
        val routeDisplayName: String = when { // Re-evaluate display name for consistency.
            !cleanedShortName.isNullOrBlank() && !cleanedLongName.isNullOrBlank() -> {
                if (cleanedLongName.contains(cleanedShortName) || cleanedShortName == cleanedLongName) cleanedLongName else "$cleanedShortName - $cleanedLongName"
            }
            !cleanedShortName.isNullOrBlank() -> cleanedShortName
            !cleanedLongName.isNullOrBlank() -> cleanedLongName
            else -> "N/A"
        }

        // Create a new Ticket object.
        val newTicket = Ticket(
            userId = currentUser.uid, // Associate ticket with current user's UID.
            routeNameDisplay = "$routeDisplayName (${getRouteTypeName(route.routeType)})",
            routeShortName = route.routeShortName,
            routeLongName = route.routeLongName,
            routeType = route.routeType,
            originStopName = suggestion.originStop.stopName,
            destinationStopName = suggestion.destinationStop.stopName,
            originDepartureTime = suggestion.originDepartureTime,
            destinationArrivalTime = suggestion.destinationArrivalTime,
            fare = 2.50, // Mock fare.
            qrCodeData = "MOCK_QR_${UUID.randomUUID()}", // Generate unique mock QR data.
            purchaseTimestamp = null // Firestore will set this with @ServerTimestamp.
        )

        // Add the new ticket to the "tickets" collection in Firestore.
        db.collection("tickets")
            .add(newTicket)
            .addOnSuccessListener { documentReference ->
                // Handle successful ticket creation.
                Log.d(TAG, "Ticket purchased successfully! DocumentID: ${documentReference.id}")
                Toast.makeText(requireContext(), "Ticket 'purchased' successfully!", Toast.LENGTH_SHORT).show()
                // Reset UI after purchase: hide card, show suggestions list, clear polyline and selection.
                binding.cardViewSelectedRouteInfo.visibility = View.GONE
                binding.recyclerViewRouteSuggestions.visibility = View.VISIBLE // Or hide if preferred.
                currentRoutePolyline?.remove()
                currentRoutePolyline = null
                currentSelectedSuggestion = null
            }
            .addOnFailureListener { e ->
                // Handle failure to save ticket.
                Log.w(TAG, "Error purchasing ticket", e)
                Toast.makeText(requireContext(), "Error purchasing ticket: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Called when the view previously created by onCreateView() has been detached from the fragment.
     * Cleans up resources like the GoogleMap instance, ViewBinding, and selected suggestion.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        googleMap = null // Release GoogleMap instance to prevent memory leaks.
        _binding = null // Clear ViewBinding reference.
        currentSelectedSuggestion = null // Clear selected suggestion.
    }
}