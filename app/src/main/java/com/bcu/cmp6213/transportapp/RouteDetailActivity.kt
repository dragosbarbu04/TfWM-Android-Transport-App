// File: app/src/main/java/com/bcu/cmp6213/transportapp/RouteDetailActivity.kt
package com.bcu.cmp6213.transportapp

// Android SDK and AndroidX imports
import android.os.Build // Provides information about the current device's build (e.g., for version-specific API calls).
import android.os.Bundle // Used for saving and restoring activity state, and for passing data between activities.
import android.util.Log // For logging messages.
import android.view.MenuItem // Represents an item in an options menu (used here for ActionBar items).
import android.view.View // Base class for UI components.
import android.widget.Toast // For showing short messages to the user.
import androidx.appcompat.app.AppCompatActivity // Base class for activities that use the support library action bar features.
import androidx.recyclerview.widget.LinearLayoutManager // For arranging items in a vertical list in RecyclerView.

// App-specific imports for data classes, data binding, and RecyclerView adapter.
import com.bcu.cmp6213.transportapp.data.Stop // Data class representing a single stop.
import com.bcu.cmp6213.transportapp.databinding.ActivityRouteDetailBinding // ViewBinding class for activity_route_detail.xml.
import com.bcu.cmp6213.transportapp.utils.StopAdapter // RecyclerView adapter for displaying Stop items.

/**
 * An [AppCompatActivity] that displays the detailed sequence of stops for a selected transport route.
 * It receives the route name and a list of [Stop] objects via Intent extras from the calling
 * activity or fragment (e.g., [RoutesFragment]).
 */
class RouteDetailActivity : AppCompatActivity() {

    // ViewBinding instance for accessing views in activity_route_detail.xml.
    private lateinit var binding: ActivityRouteDetailBinding
    // Adapter for the RecyclerView that will display the list of Stop objects.
    private lateinit var stopAdapter: StopAdapter
    // Note: No ViewModel is strictly needed in this Activity if all required data (route name, stops list)
    // is passed directly via the Intent. If data loading or more complex logic were required here,
    // a ViewModel would be appropriate.

    // Properties to store the route name and list of stops received from the Intent.
    private var currentRouteName: String? = "Route Details" // Default title.
    private var stopsList: ArrayList<Stop>? = null // List of stops for the current route.

    // Companion object for defining constants, such as keys for Intent extras and a log TAG.
    companion object {
        // Key for passing the route ID (currently kept for reference, though not directly used in this version's UI).
        const val EXTRA_ROUTE_ID = "com.bcu.cmp6213.transportapp.ROUTE_ID"
        // Key for passing the route name as a String extra.
        const val EXTRA_ROUTE_NAME = "com.bcu.cmp6213.transportapp.ROUTE_NAME"
        // Key for passing the list of Stop objects (as a Parcelable ArrayList) extra.
        const val EXTRA_STOPS_LIST = "com.bcu.cmp6213.transportapp.STOPS_LIST"
        // Log tag for this Activity.
        private const val TAG = "RouteDetailActivity"
    }

    /**
     * Called when the activity is first created.
     * Initializes the activity, inflates the layout, retrieves data from the launching Intent,
     * sets up the ActionBar, and configures the RecyclerView to display the stops.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     * this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Otherwise, it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate the layout using ViewBinding.
        binding = ActivityRouteDetailBinding.inflate(layoutInflater)
        // Set the content view to the root of the binding.
        setContentView(binding.root)

        // Enable the "Up" button (back arrow) in the ActionBar for navigation.
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Retrieve the route name from the Intent extra. Use a default if not provided.
        currentRouteName = intent.getStringExtra(EXTRA_ROUTE_NAME) ?: "Route Details"

        // Retrieve the ArrayList of Stop objects from the Intent extra.
        // Handles different Android versions for getting ParcelableArrayListExtra.
        stopsList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13 (Tiramisu, API 33) and above, the typed version is used.
            intent.getParcelableArrayListExtra(EXTRA_STOPS_LIST, Stop::class.java)
        } else {
            // For older versions, use the deprecated version (suppress deprecation warning).
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_STOPS_LIST)
        }

        // Set the retrieved route name in the designated TextView and as the ActionBar title.
        binding.textViewRouteNameDetail.text = currentRouteName
        supportActionBar?.title = currentRouteName

        // Initialize the RecyclerView for displaying stops.
        setupRecyclerView()

        // Check if the stopsList was successfully retrieved.
        if (stopsList != null) {
            if (stopsList!!.isNotEmpty()) {
                // If the list contains stops, submit it to the adapter for display.
                Log.d(TAG, "onCreate: Received ${stopsList!!.size} stops to display for route '$currentRouteName'.")
                stopAdapter.submitList(stopsList)
                // Make the RecyclerView visible and hide the "No Stops" message.
                binding.recyclerViewStops.visibility = View.VISIBLE
                binding.textViewNoStops.visibility = View.GONE
            } else {
                // If the list is empty, log it and show a "No stops information" message.
                Log.d(TAG, "onCreate: Received an empty list of stops for route '$currentRouteName'.")
                binding.recyclerViewStops.visibility = View.GONE
                binding.textViewNoStops.text = "No stops information available for this route."
                binding.textViewNoStops.visibility = View.VISIBLE
            }
        } else {
            // If stopsList is null (data wasn't passed correctly or retrieval failed), show an error.
            Log.e(TAG, "onCreate: Stops list is null. Cannot display details for route '$currentRouteName'.")
            Toast.makeText(this, "Error: Stops data missing for this route.", Toast.LENGTH_LONG).show()
            binding.recyclerViewStops.visibility = View.GONE
            binding.textViewNoStops.text = "Error: Could not load stop details for this route."
            binding.textViewNoStops.visibility = View.VISIBLE
            // Optionally, consider finishing the activity if this state is unrecoverable
            // and there's nothing meaningful to show the user.
            // finish();
        }
        // Hide the progress bar as data is passed directly via Intent and not loaded asynchronously here.
        binding.progressBarRouteDetail.visibility = View.GONE
    }

    /**
     * Initializes the RecyclerView, including setting its LayoutManager and Adapter ([StopAdapter]).
     */
    private fun setupRecyclerView() {
        // Create an instance of the StopAdapter.
        stopAdapter = StopAdapter()
        // Configure the RecyclerView.
        binding.recyclerViewStops.apply {
            // Set the LayoutManager that positions items in the list (vertically by default).
            layoutManager = LinearLayoutManager(this@RouteDetailActivity)
            // Set the adapter for the RecyclerView.
            adapter = stopAdapter
        }
    }

    /**
     * Handles action bar item clicks. Specifically, manages the "Up" button action.
     * @param item The menu item that was selected.
     * @return boolean Return false to allow normal menu processing to proceed,
     * true to consume it here.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Check which menu item was selected.
        when (item.itemId) {
            android.R.id.home -> {
                // If the "Up" button (home) is pressed, finish the current activity
                // to navigate back to the previous screen in the task stack.
                finish()
                return true // Indicate that the event was handled.
            }
        }
        // If the selected item is not handled here, call the superclass implementation.
        return super.onOptionsItemSelected(item)
    }
}