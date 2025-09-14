// File: com/bcu/cmp6213/transportapp/ui/routes/RoutesFragment.kt
package com.bcu.cmp6213.transportapp.routes // Changed from com.bcu.cmp6213.transportapp.routes to align with common ui structure

// AndroidX and App-specific imports
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels // For using the activityViewModels delegate to share ViewModel with MainActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
// R class import (assuming it's in the main app package, adjust if necessary)
// import com.bcu.cmp6213.transportapp.R
import com.bcu.cmp6213.transportapp.RouteDetailActivity // Activity to show details of a selected route
import com.bcu.cmp6213.transportapp.data.GtfsRoute // Data class for a GTFS route
import com.bcu.cmp6213.transportapp.data.Stop // Data class for a stop (ensure it's Parcelable for passing via Intent)
import com.bcu.cmp6213.transportapp.databinding.FragmentRoutesBinding // ViewBinding class for fragment_routes.xml
import com.bcu.cmp6213.transportapp.utils.OnRouteClickListener // Interface for route click events
import com.bcu.cmp6213.transportapp.utils.TicketAdapter // Adapter for displaying GtfsRoute items (Note: name might be confusing, it adapts GtfsRoute, not purchased Tickets)
import com.bcu.cmp6213.transportapp.viewmodel.GtfsViewModel // Shared ViewModel for GTFS data

/**
 * A [Fragment] subclass responsible for displaying a list of available GTFS routes.
 * Users can search through these routes and click on a route to see its detailed stop sequence.
 * It implements [OnRouteClickListener] to handle clicks on routes in the RecyclerView.
 */
class RoutesFragment : Fragment(), OnRouteClickListener {

    // ViewBinding instance for accessing views in fragment_routes.xml.
    // Nullable because the view hierarchy is only available between onCreateView and onDestroyView.
    private var _binding: FragmentRoutesBinding? = null
    // Non-null accessor for the binding. This property is only valid
    // between onCreateView and onDestroyView. Throws an exception if accessed outside this lifecycle.
    private val binding get() = _binding!!

    // Shared ViewModel instance, scoped to the lifecycle of the hosting Activity (MainActivity).
    // This allows data to be shared and survive configuration changes.
    private val gtfsViewModel: GtfsViewModel by activityViewModels()

    // Adapter for the RecyclerView that displays the list of GtfsRoute objects.
    // The name "TicketAdapter" might be slightly misleading here as it adapts GtfsRoute items,
    // not necessarily purchased "Tickets". Consider renaming if it causes confusion.
    private lateinit var ticketAdapter: TicketAdapter

    // Temporary storage for a GtfsRoute object that the user has clicked.
    // This is used to hold the route's context (ID, name) while its detailed stop sequence
    // is being fetched asynchronously before launching RouteDetailActivity.
    private var clickedRouteForDetail: GtfsRoute? = null

    // Companion object for constants and factory method.
    companion object {
        private const val TAG = "RoutesFragment" // TAG for logging from this fragment.
        /**
         * Factory method to create a new instance of this fragment.
         * Use this to instantiate the fragment, especially if arguments are ever needed.
         * @return A new instance of fragment RoutesFragment.
         */
        @JvmStatic
        fun newInstance() = RoutesFragment()
    }

    /**
     * Called to have the fragment instantiate its user interface view.
     * This is where the layout is inflated using ViewBinding.
     * @param inflater The LayoutInflater object that can be used to inflate any views in the fragment.
     * @param container If non-null, this is the parent view that the fragment's UI should be attached to.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state.
     * @return Return the View for the fragment's UI, or null.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment using ViewBinding.
        _binding = FragmentRoutesBinding.inflate(inflater, container, false)
        // Return the root view of the inflated layout.
        return binding.root
    }

    /**
     * Called immediately after onCreateView() has returned, but before any saved state
     * has been restored in to the view.
     * This is where UI setup (RecyclerView, listeners) and ViewModel observation are typically done.
     * @param view The View returned by onCreateView().
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize UI components and set up event listeners.
        setupRecyclerView()
        setupSearch()
        setupLoadButton()
        observeViewModel() // Start observing LiveData from the ViewModel.

        // Initial check for data in the ViewModel.
        // If routes are already loaded (e.g., by MainActivity or due to fragment recreation),
        // populate the adapter immediately.
        if (gtfsViewModel.filteredRoutes.value != null) {
            ticketAdapter.updateGtfsRoutes(gtfsViewModel.filteredRoutes.value ?: emptyList())
        } else if (gtfsViewModel.isLoading.value != true && gtfsViewModel.error.value == null) {
            // If there's no data, the ViewModel is not currently loading, and there's no error,
            // it might indicate that data hasn't been fetched yet.
            // MainActivity's initial GTFS load should typically cover this.
            // The "Refresh Routes List" button provides a manual way for the user to load data.
            // Update the label to guide the user.
            binding.textViewRoutesListLabelFragment.text = "Tap 'Refresh Routes List' to load data."
        }
    }

    /**
     * Sets up the RecyclerView, its LayoutManager, and its Adapter.
     * The adapter uses `this` fragment as the OnRouteClickListener.
     */
    private fun setupRecyclerView() {
        // Initialize the TicketAdapter, passing 'this' fragment because it implements OnRouteClickListener.
        ticketAdapter = TicketAdapter(this)
        binding.recyclerViewRoutesFragment.apply {
            // Set the LayoutManager for the RecyclerView (LinearLayoutManager for a vertical list).
            layoutManager = LinearLayoutManager(requireContext())
            // Set the adapter for the RecyclerView.
            adapter = ticketAdapter
            // Optional: Add item decoration for dividers between items.
            // addItemDecoration(DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL))
        }
    }

    /**
     * Sets up the search functionality for filtering routes.
     * Attaches a TextWatcher to the search EditText to trigger searches as the user types.
     */
    private fun setupSearch() {
        binding.editTextSearchRouteFragment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // No action needed before text changes.
            }

            /**
             * Called when the text in the search EditText changes.
             * Triggers a search in the ViewModel with the current query.
             * @param s The current text in the EditText.
             */
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                gtfsViewModel.searchRoutes(s?.toString())
            }

            override fun afterTextChanged(s: Editable?) {
                // No action needed after text changes.
            }
        })
    }

    /**
     * Sets up the "Refresh Routes List" button.
     * When clicked, it clears the current search query and triggers a forced data reload
     * (including download) in the ViewModel.
     */
    private fun setupLoadButton() {
        binding.buttonLoadRoutesFragment.setOnClickListener {
            binding.editTextSearchRouteFragment.text?.clear() // Clear any existing search text.
            Toast.makeText(requireContext(), "Refreshing routes list...", Toast.LENGTH_SHORT).show()
            // Call loadGtfsData with forceDownload = true to ensure fresh data.
            gtfsViewModel.loadGtfsData(forceDownload = true)
        }
    }

    /**
     * Sets up observers for LiveData objects exposed by the [GtfsViewModel].
     * This allows the UI to reactively update when data changes (routes, loading state, errors).
     */
    private fun observeViewModel() {
        // Observe changes to the list of filtered routes.
        gtfsViewModel.filteredRoutes.observe(viewLifecycleOwner, Observer { routes ->
            // Update the adapter with the new list of routes. If routes is null, pass an empty list.
            ticketAdapter.updateGtfsRoutes(routes ?: emptyList())
            Log.d(TAG, "Filtered routes updated in fragment: ${routes?.size ?: 0} routes")

            val searchText = binding.editTextSearchRouteFragment.text?.toString() ?: ""
            // Update the label above the RecyclerView based on the routes list and search state.
            if (routes.isNullOrEmpty()) {
                if (searchText.isNotEmpty()) {
                    binding.textViewRoutesListLabelFragment.text = "No routes match your search."
                } else if (gtfsViewModel.isLoading.value == false && gtfsViewModel.error.value == null) {
                    // If not loading, no error, and no search text, it means no routes are loaded.
                    binding.textViewRoutesListLabelFragment.text = "No routes loaded. Tap 'Refresh'."
                }
                // The error case is handled by the gtfsViewModel.error observer below.
            } else {
                // If routes are present, show the default label.
                binding.textViewRoutesListLabelFragment.text = "Available TfWM Routes:"
            }
        })

        // Observe the global loading state from the ViewModel.
        // This controls the visibility of a ProgressBar specific to this fragment.
        // Note: MainActivity might also observe this for a global ProgressBar.
        gtfsViewModel.isLoading.observe(viewLifecycleOwner, Observer { isLoading ->
            // Show/hide the ProgressBar based on the loading state.
            binding.progressBarRoutesFragment.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Enable/disable the refresh button based on the loading state.
            binding.buttonLoadRoutesFragment.isEnabled = !isLoading
            // Check if _binding is not null to prevent crashes if the view is already destroyed
            // when this observer fires (e.g., due to rapid configuration changes or navigation).
            if (isLoading && _binding != null) {
                binding.textViewRoutesListLabelFragment.text = "Loading TfWM Routes..."
            }
            // The label text will be further updated by the filteredRoutes or error observer
            // once loading finishes.
        })

        // Observe error messages from the ViewModel.
        gtfsViewModel.error.observe(viewLifecycleOwner, Observer { errorMessage ->
            errorMessage?.let {
                // Check if the view binding is still valid before trying to update UI.
                if(_binding == null) return@Observer

                Log.e(TAG, "Error observed in fragment: $it")
                binding.textViewRoutesListLabelFragment.text = "Error loading routes. Tap 'Refresh'."
                // A global Toast might be shown by MainActivity. For fragment-specific Toasts, uncomment below:
                // Toast.makeText(requireContext(), "Error: $it", Toast.LENGTH_LONG).show()
                // If there's an error and the current list is empty, ensure the adapter is cleared.
                if(gtfsViewModel.filteredRoutes.value.isNullOrEmpty()){
                    ticketAdapter.updateGtfsRoutes(emptyList())
                }
            }
        })

        // Observe the loading state specifically for route details (stops).
        // This is used to manage UI feedback (e.g., Toast) before launching RouteDetailActivity.
        gtfsViewModel.isLoadingDetails.observe(viewLifecycleOwner, Observer { isLoading ->
            if (isLoading) {
                // Show a Toast message when details are being loaded.
                Toast.makeText(requireContext(), "Loading route details...", Toast.LENGTH_SHORT).show()
            } else {
                // Details are no longer loading.
                // Check if there was a route click pending (`clickedRouteForDetail`).
                val currentClickedRoute = clickedRouteForDetail
                if (currentClickedRoute != null) {
                    // Get the loaded stops and any detail error from the ViewModel.
                    val stops = gtfsViewModel.selectedRouteStopSequence.value
                    val detailError = gtfsViewModel.detailError.value

                    if (detailError != null) {
                        // If there was an error loading stops, show a Toast and log it.
                        Toast.makeText(requireContext(), "Error loading stops: $detailError", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Not launching detail for ${currentClickedRoute.routeId} due to error: $detailError")
                    } else if (stops != null) {
                        // If stops are available (can be an empty list if the route has no stops),
                        // launch the RouteDetailActivity.
                        Log.d(TAG, "Details ready for ${currentClickedRoute.routeId}. Launching with ${stops.size} stops.")
                        launchRouteDetailActivity(currentClickedRoute, ArrayList(stops))
                    } else {
                        // If stops are null and there's no error, something unexpected happened.
                        Toast.makeText(requireContext(), "Could not retrieve stop details.", Toast.LENGTH_LONG).show()
                        Log.w(TAG, "Not launching detail for ${currentClickedRoute.routeId}: stops is null and no error.")
                    }
                    // Reset clickedRouteForDetail after attempting to handle the click,
                    // to prevent re-launching if the observer fires again for another reason.
                    clickedRouteForDetail = null
                }
            }
        })

        // Observe errors specifically related to loading route details.
        gtfsViewModel.detailError.observe(viewLifecycleOwner, Observer { detailErrorMessage ->
            // If there's a detail error and a route click was pending, log it and clear the pending click.
            // The Toast for this error is likely already shown by the isLoadingDetails observer when it transitions to false.
            if (detailErrorMessage != null && clickedRouteForDetail != null) {
                Log.e(TAG, "Detail error for pending click on ${clickedRouteForDetail!!.routeId}: $detailErrorMessage")
                clickedRouteForDetail = null // Reset the pending click context.
            }
        })

        // Observe the selectedRouteStopSequence LiveData.
        // In this fragment, this observer is primarily for logging and debugging purposes,
        // as the actual launch of RouteDetailActivity is triggered by the isLoadingDetails observer's state change.
        gtfsViewModel.selectedRouteStopSequence.observe(viewLifecycleOwner, Observer { stops ->
            Log.d(TAG, "selectedRouteStopSequence LiveData updated in RoutesFragment. Count: ${stops?.size}. IsLoadingDetails: ${gtfsViewModel.isLoadingDetails.value}")
        })
    }

    // --- OnRouteClickListener Implementation ---
    /**
     * Callback method from [OnRouteClickListener] interface, invoked when a route is clicked
     * in the RecyclerView (via [TicketAdapter]).
     * @param route The [GtfsRoute] object that was clicked.
     */
    override fun onRouteClick(route: GtfsRoute) {
        Log.d(TAG, "Route clicked in fragment: ID = ${route.routeId}, Name = ${route.routeShortName ?: route.routeLongName}")
        // Store the clicked route. This allows us to use its details (name, ID) when the
        // asynchronous loading of its stop sequence completes.
        clickedRouteForDetail = route
        // Trigger the ViewModel to fetch the stop sequence for the selected route.
        // The actual navigation to RouteDetailActivity will be handled by the observer
        // for `isLoadingDetails` once the data is ready or an error occurs.
        gtfsViewModel.getStopSequenceForRoute(route.routeId)
    }

    /**
     * Launches the [RouteDetailActivity] to display the stops for a given route.
     * @param route The [GtfsRoute] object for which details are being shown.
     * @param stops An [ArrayList] of [Stop] objects representing the sequence of stops for the route.
     * The [Stop] class must be Parcelable.
     */
    private fun launchRouteDetailActivity(route: GtfsRoute, stops: ArrayList<Stop>) {
        // Prepare a user-friendly display name for the route.
        val cleanedShortName = route.routeShortName?.trim()?.removeSurrounding("\"")
        val cleanedLongName = route.routeLongName?.trim()?.removeSurrounding("\"")
        val routeDisplayName = when {
            !cleanedShortName.isNullOrBlank() && !cleanedLongName.isNullOrBlank() ->
                if (cleanedLongName.contains(cleanedShortName) || cleanedShortName == cleanedLongName) cleanedLongName else "$cleanedShortName - $cleanedLongName"
            !cleanedShortName.isNullOrBlank() -> cleanedShortName
            !cleanedLongName.isNullOrBlank() -> cleanedLongName
            else -> "Route Details" // Fallback title.
        }

        // Create an Intent to start RouteDetailActivity.
        val intent = Intent(activity, RouteDetailActivity::class.java).apply {
            // Pass necessary data to RouteDetailActivity as Intent extras.
            putExtra(RouteDetailActivity.EXTRA_ROUTE_ID, route.routeId)
            putExtra(RouteDetailActivity.EXTRA_ROUTE_NAME, routeDisplayName)
            // Pass the list of stops. This requires the Stop class to be Parcelable.
            putParcelableArrayListExtra(RouteDetailActivity.EXTRA_STOPS_LIST, stops)
        }
        startActivity(intent) // Start the activity.
    }

    /**
     * Called when the view previously created by onCreateView() has been detached from the fragment.
     * The next time the fragment needs to be displayed, a new view will be created.
     * This is where we clean up the ViewBinding instance to prevent memory leaks.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        // Nullify the binding object when the view is destroyed to avoid holding onto view references,
        // preventing memory leaks.
        _binding = null
    }
}