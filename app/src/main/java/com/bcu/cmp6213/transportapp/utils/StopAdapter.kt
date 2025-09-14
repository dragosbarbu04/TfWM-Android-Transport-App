// File: app/src/main/java/com/bcu/cmp6213/transportapp/utils/StopAdapter.kt
package com.bcu.cmp6213.transportapp.utils

// Android and RecyclerView imports
import android.view.LayoutInflater // Used to inflate layout XML files into their corresponding View objects.
import android.view.ViewGroup // Represents a container that can hold other Views.
import androidx.recyclerview.widget.DiffUtil // Utility class for calculating differences between two lists.
import androidx.recyclerview.widget.ListAdapter // RecyclerView.Adapter base class for presenting List data in a RecyclerView, including computing diffs between Lists on a background thread.
import androidx.recyclerview.widget.RecyclerView // Core class for displaying large data sets efficiently.
// App-specific data and binding imports
import com.bcu.cmp6213.transportapp.data.Stop // Your Stop data class, representing a single stop.
import com.bcu.cmp6213.transportapp.databinding.ItemStopBinding // ViewBinding class generated for your item_stop.xml layout.

/**
 * A RecyclerView Adapter for displaying a list of [Stop] objects.
 * It uses [ListAdapter] with [DiffUtil] for efficient updates when the list of stops changes.
 * Each stop is displayed using the `item_stop.xml` layout, showing its sequence number and name.
 */
class StopAdapter : ListAdapter<Stop, StopAdapter.StopViewHolder>(StopDiffCallback()) {

    /**
     * Called when RecyclerView needs a new [StopViewHolder] of the given type to represent an item.
     * This new ViewHolder will be used to display items of the adapter using onBindViewHolder.
     * @param parent The ViewGroup into which the new View will be added after it is bound to an adapter position.
     * @param viewType The view type of the new View.
     * @return A new StopViewHolder that holds a View of the given view type.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StopViewHolder {
        // Inflate the item_stop.xml layout using ViewBinding.
        // LayoutInflater.from(parent.context) gets the LayoutInflater from the parent's context.
        val binding = ItemStopBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Create and return a new StopViewHolder instance with the inflated binding.
        return StopViewHolder(binding)
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * This method should update the contents of the [StopViewHolder.itemView] to reflect the item at the
     * given position.
     * @param holder The StopViewHolder which should be updated to represent the contents of the
     * item at the given position in the data set.
     * @param position The position of the item within the adapter's data set.
     */
    override fun onBindViewHolder(holder: StopViewHolder, position: Int) {
        // Get the Stop object at the current position from the list submitted to the adapter.
        val stopItem = getItem(position)
        // Bind the data from the Stop object to the views in the ViewHolder.
        // Pass `position + 1` to display a 1-based sequence number for the stop in the list.
        holder.bind(stopItem, position + 1)
    }

    /**
     * ViewHolder class for displaying individual [Stop] items.
     * It holds references to the views within the `item_stop.xml` layout,
     * managed by the [ItemStopBinding].
     * @param binding The ViewBinding instance for the item_stop.xml layout.
     */
    inner class StopViewHolder(private val binding: ItemStopBinding) :
        RecyclerView.ViewHolder(binding.root) { // itemView is the root view of the binding.

        /**
         * Binds the data from a [Stop] object and its sequence number to the views in this ViewHolder.
         * @param stop The [Stop] object containing the data to display.
         * @param sequenceNumber The 1-based sequence number of this stop in the list.
         */
        fun bind(stop: Stop, sequenceNumber: Int) {
            // Display the sequence number (e.g., "1.", "2.") in the textViewStopSequence.
            binding.textViewStopSequence.text = "${sequenceNumber}."

            // Display the stop name.
            // Handle cases where the stop name might be null or blank by providing a fallback display.
            val stopNameDisplay = if (stop.stopName.isNullOrBlank()) {
                "Unnamed Stop (ID: ${stop.stopId})" // Fallback text if name is missing.
            } else {
                stop.stopName // Use the actual stop name.
            }
            binding.textViewStopName.text = stopNameDisplay

            // Example of how you might display other stop information if your item_stop.xml had more views.
            // For instance, if you had a TextView for stop times or coordinates:
            // binding.textViewStopTime.text = "Lat: ${stop.stopLat ?: "N/A"}" // Example, assuming textViewStopTime exists in item_stop.xml
        }
    }

    /**
     * DiffUtil.ItemCallback for calculating the difference between two non-null [Stop] items in a list.
     * This allows [ListAdapter] to use efficient animations and updates when the list changes.
     */
    class StopDiffCallback : DiffUtil.ItemCallback<Stop>() {
        /**
         * Called to check whether two objects represent the same item.
         * For example, if your items have unique IDs, this method should check their id equality.
         * @param oldItem The [Stop] item in the old list.
         * @param newItem The [Stop] item in the new list.
         * @return True if the two items represent the same object (based on their unique stopId),
         * false otherwise.
         */
        override fun areItemsTheSame(oldItem: Stop, newItem: Stop): Boolean {
            // Each stop should have a unique stopId.
            return oldItem.stopId == newItem.stopId
        }

        /**
         * Called to check whether two items have the same data.
         * This information is used to detect if the contents of an item have changed.
         * This method is called only if [areItemsTheSame] returns true for these items.
         * @param oldItem The [Stop] item in the old list.
         * @param newItem The [Stop] item in the new list.
         * @return True if the contents of the items are the same, false otherwise.
         */
        override fun areContentsTheSame(oldItem: Stop, newItem: Stop): Boolean {
            // Since Stop is a data class, the '==' operator performs a structural comparison
            // of all its properties (stopId, stopName, etc.).
            return oldItem == newItem
        }
    }
}