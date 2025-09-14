// File: app/src/main/java/com/bcu/cmp6213/transportapp/utils/TicketAdapter.kt
package com.bcu.cmp6213.transportapp.utils

// Android and RecyclerView imports
import android.view.LayoutInflater // Used to inflate layout XML files.
import android.view.ViewGroup // Represents a container for Views.
import androidx.recyclerview.widget.DiffUtil // Utility for calculating list differences.
import androidx.recyclerview.widget.ListAdapter // RecyclerView.Adapter for List data with DiffUtil support.
import androidx.recyclerview.widget.RecyclerView // Core class for displaying lists.
// App-specific data and binding imports
import com.bcu.cmp6213.transportapp.data.GtfsRoute // Data class for a GTFS route.
import com.bcu.cmp6213.transportapp.databinding.ItemTicketBinding // ViewBinding class for item_ticket.xml.

/**
 * Interface definition for a callback to be invoked when a route item in the list is clicked.
 */
interface OnRouteClickListener {
    /**
     * Called when a [GtfsRoute] item has been clicked.
     * @param route The [GtfsRoute] object that was clicked.
     */
    fun onRouteClick(route: GtfsRoute)
}

/**
 * A RecyclerView Adapter for displaying a list of [GtfsRoute] objects.
 * It uses [ListAdapter] with [DiffUtil] for efficient updates when the list of routes changes.
 * Each route is displayed using the `item_ticket.xml` layout, where the fields of
 * `item_ticket.xml` are repurposed to show details of a [GtfsRoute].
 *
 * @param clickListener An [OnRouteClickListener] to handle click events on route items.
 */
class TicketAdapter(private val clickListener: OnRouteClickListener) :
    ListAdapter<GtfsRoute, TicketAdapter.TicketViewHolder>(GtfsRouteDiffCallback()) {

    /**
     * Called when RecyclerView needs a new [TicketViewHolder] of the given type to represent an item.
     * Inflates the item layout (`item_ticket.xml`) and creates the ViewHolder.
     * @param parent The ViewGroup into which the new View will be added.
     * @param viewType The view type of the new View.
     * @return A new TicketViewHolder that holds the View for a GtfsRoute item.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        // Inflate the item_ticket.xml layout using ViewBinding.
        val binding = ItemTicketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Create and return a new TicketViewHolder instance.
        return TicketViewHolder(binding)
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * Updates the contents of the [TicketViewHolder.itemView] to reflect the GtfsRoute item.
     * @param holder The TicketViewHolder to be updated.
     * @param position The position of the item within the adapter's data set.
     */
    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        // Get the GtfsRoute object at the current position.
        val routeItem = getItem(position)
        // Bind the data from the routeItem to the views in the ViewHolder,
        // and pass the clickListener for handling item clicks.
        holder.bind(routeItem, clickListener)
    }

    /**
     * A convenience method to update the list of GTFS routes displayed by this adapter.
     * This simply calls `submitList`, which is the standard way to update data in a [ListAdapter].
     * @param newRoutes The new list of [GtfsRoute] objects to display.
     */
    fun updateGtfsRoutes(newRoutes: List<GtfsRoute>) {
        submitList(newRoutes)
    }

    /**
     * ViewHolder class for displaying individual [GtfsRoute] items.
     * Holds references to the views within the `item_ticket.xml` layout,
     * managed by [ItemTicketBinding].
     * @param binding The ViewBinding instance for the item_ticket.xml layout.
     */
    inner class TicketViewHolder(private val binding: ItemTicketBinding) :
        RecyclerView.ViewHolder(binding.root) { // itemView is the root of the binding.

        /**
         * Binds the data from a [GtfsRoute] object to the views in this ViewHolder.
         * The views from `item_ticket.xml` are repurposed here:
         * - `textViewRouteName` shows the route's display name.
         * - `textViewFare` shows the route type (e.g., "Bus", "Tram").
         * - `textViewPurchaseTime` shows the agency ID.
         * - `textViewQrCode` shows the route ID.
         *
         * Also sets up an OnClickListener for the item view.
         * @param route The [GtfsRoute] object containing the data to display.
         * @param listener The [OnRouteClickListener] to handle item clicks.
         */
        fun bind(route: GtfsRoute, listener: OnRouteClickListener) {
            // --- Prepare Route Display Name ---
            // Clean up route short and long names (trim whitespace, remove surrounding quotes).
            val cleanedShortName = route.routeShortName?.trim()?.removeSurrounding("\"")
            val cleanedLongName = route.routeLongName?.trim()?.removeSurrounding("\"")

            val routeDisplayName: String
            val hasShortName = !cleanedShortName.isNullOrBlank()
            val hasLongName = !cleanedLongName.isNullOrBlank()

            // Construct a user-friendly display name for the route.
            routeDisplayName = when {
                hasShortName && hasLongName -> {
                    // If both names exist, prioritize long name if it contains short name, or combine them.
                    if (cleanedLongName!!.contains(cleanedShortName!!) || cleanedShortName == cleanedLongName) {
                        cleanedLongName
                    } else {
                        "$cleanedShortName - $cleanedLongName"
                    }
                }
                hasShortName -> cleanedShortName!! // Use short name if only it exists.
                hasLongName -> cleanedLongName!!   // Use long name if only it exists.
                else -> "N/A" // Fallback if both are missing.
            }
            // Set the main route name display.
            binding.textViewRouteName.text = "Route: $routeDisplayName"

            // --- Repurpose ItemTicketBinding fields for GtfsRoute details ---
            // Use textViewFare to display the route type.
            binding.textViewFare.text = "Type: ${getRouteTypeName(route.routeType)}"
            // Use textViewPurchaseTime to display the agency ID.
            binding.textViewPurchaseTime.text = "Agency: ${route.agencyId?.trim()?.removeSurrounding("\"") ?: "N/A"}"
            // Use textViewQrCode to display the route ID (for debugging/info).
            binding.textViewQrCode.text = "Route ID: ${route.routeId}"

            // --- Set Click Listener ---
            // Set an OnClickListener on the entire item view.
            itemView.setOnClickListener {
                // When clicked, invoke the onRouteClick callback of the listener,
                // passing the current GtfsRoute object.
                listener.onRouteClick(route)
            }
        }

        /**
         * Helper function to convert a GTFS route type code (String) into a human-readable name.
         * @param routeType The GTFS route type code (e.g., "0" for Tram, "3" for Bus).
         * @return A human-readable string for the route type, or "Unknown Type" with the code.
         */
        private fun getRouteTypeName(routeType: String?): String {
            return when (routeType?.trim()) {
                "0" -> "Tram/Streetcar"
                "1" -> "Subway/Metro"
                "2" -> "Rail"
                "3" -> "Bus"
                "4" -> "Ferry"
                "5" -> "Cable Car"
                "6" -> "Gondola"
                "7" -> "Funicular"
                else -> "Unknown Type (${routeType ?: "N/A"})" // Fallback for unmapped or null types.
            }
        }
    }

    /**
     * DiffUtil.ItemCallback for calculating the difference between two [GtfsRoute] items.
     * This helps ListAdapter efficiently update the RecyclerView when the underlying data changes.
     */
    class GtfsRouteDiffCallback : DiffUtil.ItemCallback<GtfsRoute>() {
        /**
         * Called to check whether two objects represent the same item.
         * For GtfsRoute, `routeId` is its unique identifier.
         * @param oldItem The item in the old list.
         * @param newItem The item in the new list.
         * @return True if the two items have the same `routeId`.
         */
        override fun areItemsTheSame(oldItem: GtfsRoute, newItem: GtfsRoute): Boolean {
            return oldItem.routeId == newItem.routeId
        }

        /**
         * Called to check whether two items have the same data.
         * This is called only if [areItemsTheSame] returns true.
         * @param oldItem The item in the old list.
         * @param newItem The item in the new list.
         * @return True if the contents of the items are the same.
         */
        override fun areContentsTheSame(oldItem: GtfsRoute, newItem: GtfsRoute): Boolean {
            // GtfsRoute is a data class, so '==' performs a structural comparison of all properties.
            return oldItem == newItem
        }
    }
}