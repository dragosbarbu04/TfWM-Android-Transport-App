// File: com/bcu/cmp6213/transportapp/utils/RouteSuggestionAdapter.kt
package com.bcu.cmp6213.transportapp.utils

// Android and RecyclerView imports
import android.view.LayoutInflater // Used to inflate layout XML files.
import android.view.ViewGroup // Represents a container for Views.
import androidx.recyclerview.widget.DiffUtil // Utility for calculating list differences.
import androidx.recyclerview.widget.ListAdapter // RecyclerView.Adapter for List data with DiffUtil support.
import androidx.recyclerview.widget.RecyclerView // Core class for displaying lists.
// App-specific data and binding imports
import com.bcu.cmp6213.transportapp.data.SuggestedRouteOption // Data class for a single route suggestion.
import com.bcu.cmp6213.transportapp.databinding.ItemRouteSuggestionBinding // ViewBinding class for item_route_suggestion.xml.

/**
 * Interface definition for a callback to be invoked when a route suggestion item is clicked.
 */
interface OnRouteSuggestionClickListener {
    /**
     * Called when a route suggestion item has been clicked.
     * @param suggestion The [SuggestedRouteOption] object that was clicked.
     */
    fun onSuggestionClick(suggestion: SuggestedRouteOption)
}

/**
 * A RecyclerView Adapter for displaying a list of [SuggestedRouteOption] objects.
 * It uses [ListAdapter] with [DiffUtil] for efficient updates when the list of suggestions changes.
 * Each suggestion is displayed using the `item_route_suggestion.xml` layout.
 *
 * @param clickListener An optional listener to handle click events on suggestion items.
 */
class RouteSuggestionAdapter(
    private val clickListener: OnRouteSuggestionClickListener? = null
) : ListAdapter<SuggestedRouteOption, RouteSuggestionAdapter.SuggestionViewHolder>(SuggestionDiffCallback()) {

    /**
     * Called when RecyclerView needs a new [SuggestionViewHolder] of the given type to represent an item.
     * Inflates the item layout and creates the ViewHolder.
     * @param parent The ViewGroup into which the new View will be added.
     * @param viewType The view type of the new View.
     * @return A new SuggestionViewHolder that holds the View for a suggestion item.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        // Inflate the item_route_suggestion.xml layout using ViewBinding.
        val binding = ItemRouteSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Create and return a new SuggestionViewHolder instance.
        return SuggestionViewHolder(binding)
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * Updates the contents of the [SuggestionViewHolder.itemView] to reflect the item.
     * @param holder The SuggestionViewHolder to be updated.
     * @param position The position of the item within the adapter's data set.
     */
    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        // Get the SuggestedRouteOption object at the current position.
        val suggestionItem = getItem(position)
        // Bind the data from the suggestionItem to the views in the ViewHolder,
        // and pass the clickListener.
        holder.bind(suggestionItem, clickListener)
    }

    /**
     * ViewHolder class for displaying individual [SuggestedRouteOption] items.
     * Holds references to the views within the `item_route_suggestion.xml` layout,
     * managed by [ItemRouteSuggestionBinding].
     * @param binding The ViewBinding instance for the item layout.
     */
    inner class SuggestionViewHolder(private val binding: ItemRouteSuggestionBinding) :
        RecyclerView.ViewHolder(binding.root) { // itemView is the root of the binding.

        /**
         * Binds the data from a [SuggestedRouteOption] object to the views in this ViewHolder.
         * Also sets up an OnClickListener for the item view if a listener is provided.
         * @param suggestion The [SuggestedRouteOption] object containing the data to display.
         * @param listener The [OnRouteSuggestionClickListener] to handle item clicks.
         */
        fun bind(suggestion: SuggestedRouteOption, listener: OnRouteSuggestionClickListener?) {
            val route = suggestion.route // Get the GtfsRoute object from the suggestion.

            // --- Prepare Route Display Name ---
            // Clean up route short and long names (trim whitespace, remove surrounding quotes).
            val cleanedShortName = route.routeShortName?.trim()?.removeSurrounding("\"")
            val cleanedLongName = route.routeLongName?.trim()?.removeSurrounding("\"")
            // Construct a user-friendly display name for the route.
            val routeDisplayName: String = when {
                !cleanedShortName.isNullOrBlank() && !cleanedLongName.isNullOrBlank() -> {
                    // If both short and long names exist, prioritize long name if it contains short name,
                    // or combine them.
                    if (cleanedLongName.contains(cleanedShortName) || cleanedShortName == cleanedLongName) {
                        cleanedLongName
                    } else {
                        "$cleanedShortName - $cleanedLongName"
                    }
                }
                !cleanedShortName.isNullOrBlank() -> cleanedShortName // Use short name if only it exists.
                !cleanedLongName.isNullOrBlank() -> cleanedLongName   // Use long name if only it exists.
                else -> "N/A" // Fallback if both are missing.
            }
            // Set the route name TextView, including the route type.
            binding.textViewSuggestionRouteName.text = "Route: $routeDisplayName (${getRouteTypeName(route.routeType)})"

            // --- Set Origin Stop Information ---
            val originStopName = suggestion.originStop.stopName?.trim()?.removeSurrounding("\"") ?: "Unknown Origin"
            binding.textViewSuggestionOriginStop.text = "From: $originStopName"

            // Set Origin Departure Time, formatted.
            binding.textViewSuggestionOriginTime.text = if (!suggestion.originDepartureTime.isNullOrBlank()) {
                "Departs: ${formatTime(suggestion.originDepartureTime)}"
            } else {
                "Departure time N/A"
            }

            // --- Set Destination Stop Information ---
            val destStopName = suggestion.destinationStop.stopName?.trim()?.removeSurrounding("\"") ?: "Unknown Destination"
            binding.textViewSuggestionDestinationStop.text = "To: $destStopName"

            // Set Destination Arrival Time, formatted.
            binding.textViewSuggestionDestinationTime.text = if (!suggestion.destinationArrivalTime.isNullOrBlank()) {
                "Arrives: ${formatTime(suggestion.destinationArrivalTime)}"
            } else {
                "Arrival time N/A"
            }

            // --- Set Click Listener ---
            // If a clickListener is provided, set an OnClickListener on the entire item view.
            listener?.let {
                itemView.setOnClickListener {
                    // When clicked, invoke the onSuggestionClick callback of the listener,
                    // passing the current suggestion object.
                    listener.onSuggestionClick(suggestion)
                }
            }
        }

        /**
         * Helper function to convert a GTFS route type code (String) into a human-readable name.
         * @param routeType The GTFS route type code (e.g., "0" for Tram, "3" for Bus).
         * @return A human-readable string for the route type (e.g., "Tram", "Bus"), or "Unknown".
         */
        private fun getRouteTypeName(routeType: String?): String {
            return when (routeType?.trim()) {
                "0" -> "Tram"
                "1" -> "Subway/Metro"
                "2" -> "Rail"
                "3" -> "Bus"
                "4" -> "Ferry"
                "5" -> "Cable Car"
                "6" -> "Gondola"
                "7" -> "Funicular"
                else -> "Unknown" // Fallback for unmapped or null route types.
            }
        }

        /**
         * Helper function to format GTFS time strings (typically HH:MM:SS)
         * into a more concise HH:MM format for display.
         * It handles GTFS times that can be greater than 24:00:00 (for overnight services)
         * by simply taking the HH:MM part.
         * @param gtfsTime The GTFS time string (e.g., "10:05:00", "25:30:00").
         * @return A formatted time string in "HH:MM" format, or "N/A" if input is null/blank,
         * or the original string if it cannot be simply split.
         */
        private fun formatTime(gtfsTime: String?): String {
            if (gtfsTime.isNullOrBlank()) return "N/A" // Return "N/A" for null or blank input.
            // GTFS time is usually HH:MM:SS. We typically want to display only HH:MM.
            val parts = gtfsTime.split(":") // Split the time string by colons.
            return if (parts.size >= 2) {
                // If we have at least hours and minutes, construct "HH:MM".
                // This handles hours > 23 correctly for display purposes (e.g., "25:30").
                "${parts[0]}:${parts[1]}"
            } else {
                gtfsTime // Return original string if it's not in the expected "HH:MM:SS" format.
            }
        }
    }

    /**
     * DiffUtil.ItemCallback for calculating the difference between two [SuggestedRouteOption] items.
     * This helps ListAdapter efficiently update the RecyclerView when the underlying data changes.
     */
    class SuggestionDiffCallback : DiffUtil.ItemCallback<SuggestedRouteOption>() {
        /**
         * Called to check whether two objects represent the same item.
         * Compares items based on a composite key of their core identifying properties
         * (route ID, origin stop ID, destination stop ID, and trip ID).
         * @param oldItem The item in the old list.
         * @param newItem The item in the new list.
         * @return True if the two items represent the same logical suggestion.
         */
        override fun areItemsTheSame(oldItem: SuggestedRouteOption, newItem: SuggestedRouteOption): Boolean {
            // Consider items the same if their main identifiers match.
            return oldItem.route.routeId == newItem.route.routeId &&
                    oldItem.originStop.stopId == newItem.originStop.stopId &&
                    oldItem.destinationStop.stopId == newItem.destinationStop.stopId &&
                    oldItem.tripId == newItem.tripId // Trip ID is important for uniqueness of a specific service instance.
        }

        /**
         * Called to check whether two items have the same data.
         * This is called only if [areItemsTheSame] returns true.
         * @param oldItem The item in the old list.
         * @param newItem The item in the new list.
         * @return True if the contents of the items are the same.
         */
        override fun areContentsTheSame(oldItem: SuggestedRouteOption, newItem: SuggestedRouteOption): Boolean {
            // Since SuggestedRouteOption is a data class, '==' performs a structural comparison
            // of all its properties.
            return oldItem == newItem
        }
    }
}