// File: com/bcu/cmp6213/transportapp/ui/tickets/TicketDisplayAdapter.kt
package com.bcu.cmp6213.transportapp.ui.tickets

// Android and Java utility imports
import android.view.LayoutInflater // Used to inflate layout XML files into their corresponding View objects.
import android.view.ViewGroup // Represents a container that can hold other Views.
import androidx.recyclerview.widget.DiffUtil // Utility class for calculating differences between two lists.
import androidx.recyclerview.widget.ListAdapter // RecyclerView.Adapter base class for presenting List data in a RecyclerView, including computing diffs between Lists on a background thread.
import androidx.recyclerview.widget.RecyclerView // Core class for displaying large data sets efficiently.
// App-specific data and binding imports
import com.bcu.cmp6213.transportapp.data.Ticket // The data class representing a purchased ticket.
import com.bcu.cmp6213.transportapp.databinding.ItemDisplayTicketBinding // ViewBinding class for item_display_ticket.xml.
import java.text.SimpleDateFormat // For formatting dates and times.
import java.util.Locale // For locale-specific formatting (e.g., currency, date format).
import java.util.TimeZone // For handling timezones, though GTFS times are often local or relative.

/**
 * A RecyclerView Adapter for displaying a list of [Ticket] objects.
 * It uses [ListAdapter] with [DiffUtil] for efficient updates when the list of tickets changes.
 * Each ticket is displayed using the `item_display_ticket.xml` layout.
 */
class TicketDisplayAdapter : ListAdapter<Ticket, TicketDisplayAdapter.TicketViewHolder>(TicketDiffCallback()) {

    // Date formatter for displaying the ticket's purchase timestamp in a user-friendly format.
    // Example format: "17 May 2025, 10:00"
    private val outputDateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    // Note: The following SimpleDateFormat instances for GTFS time (gtfsTimeFormat, displayTimeFormat)
    // are initialized but the actual time formatting for departure/arrival in `formatDisplayTime`
    // uses a simpler string split method. This is because GTFS times can exceed 24:00:00
    // (e.g., "25:30:00" for 1:30 AM next day), which SimpleDateFormat doesn't handle natively.
    // The string split approach is more robust for this specific GTFS time characteristic.
    private val gtfsTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val displayTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    init {
        // GTFS times are often timezone-agnostic or based on the local agency time.
        // If precise timezone conversion were needed for SimpleDateFormat, it would be set here.
        // However, the current formatDisplayTime method bypasses complex SimpleDateFormat parsing for HH:MM.
        gtfsTimeFormat.timeZone = TimeZone.getTimeZone("UTC") // Example: Defaulting to UTC if parsing with SimpleDateFormat.
        displayTimeFormat.timeZone = TimeZone.getTimeZone("UTC") // Match above.
    }

    /**
     * Called when RecyclerView needs a new [TicketViewHolder] of the given type to represent an item.
     * This new ViewHolder should be constructed with a new View that can represent the items
     * of the given type.
     * @param parent The ViewGroup into which the new View will be added after it is bound to an adapter position.
     * @param viewType The view type of the new View.
     * @return A new TicketViewHolder that holds a View of the given view type.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        // Inflate the item_display_ticket.xml layout using ViewBinding.
        val binding = ItemDisplayTicketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Create and return a new TicketViewHolder instance with the inflated binding.
        return TicketViewHolder(binding)
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * This method should update the contents of the [TicketViewHolder.itemView] to reflect the item at the
     * given position.
     * @param holder The TicketViewHolder which should be updated to represent the contents of the item at the given position in the data set.
     * @param position The position of the item within the adapter's data set.
     */
    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        // Get the Ticket object at the current position.
        val ticket = getItem(position)
        // Bind the data from the Ticket object to the views in the ViewHolder.
        holder.bind(ticket)
    }

    /**
     * ViewHolder class for displaying individual [Ticket] items.
     * It holds references to the views within the `item_display_ticket.xml` layout,
     * managed by the [ItemDisplayTicketBinding].
     * @param binding The ViewBinding instance for the item layout.
     */
    inner class TicketViewHolder(private val binding: ItemDisplayTicketBinding) :
        RecyclerView.ViewHolder(binding.root) { // itemView is the root of the binding.

        /**
         * Binds the data from a [Ticket] object to the views in this ViewHolder.
         * @param ticket The [Ticket] object containing the data to display.
         */
        fun bind(ticket: Ticket) {
            // Set the route name display. Use "N/A" if null.
            binding.textViewTicketRouteName.text = ticket.routeNameDisplay ?: "N/A"

            // Construct and set the journey text (From origin To destination).
            val journeyText = "From: ${ticket.originStopName ?: "N/A"} To: ${ticket.destinationStopName ?: "N/A"}"
            binding.textViewTicketJourney.text = journeyText

            // Format and set departure and arrival times using the helper function.
            val formattedDeparture = formatDisplayTime(ticket.originDepartureTime)
            val formattedArrival = formatDisplayTime(ticket.destinationArrivalTime)
            binding.textViewTicketTimes.text = "Dep: $formattedDeparture Arr: $formattedArrival"

            // Format and set the mock fare. Displays with a pound symbol and two decimal places.
            binding.textViewTicketFare.text = ticket.fare?.let { "Fare: £${String.format(Locale.UK, "%.2f", it)}" } ?: "Fare: N/A"

            // Format and set the purchase timestamp using outputDateFormat.
            binding.textViewTicketPurchaseTime.text = ticket.purchaseTimestamp?.let {
                "Purchased: ${outputDateFormat.format(it)}"
            } ?: "Purchased: N/A"

            // Set the mock QR code data.
            binding.textViewTicketQrCode.text = "QR: ${ticket.qrCodeData ?: "N/A"}"
        }

        /**
         * Helper function to format GTFS time strings (which can be "HH:MM:SS" and potentially >24:00:00)
         * into a simpler "HH:MM" format for display.
         * @param gtfsTime The GTFS time string (e.g., "10:05:00", "25:30:00").
         * @return A formatted time string in "HH:MM" format, or "N/A" if the input is null/blank,
         * or the original string if it cannot be simply split.
         */
        private fun formatDisplayTime(gtfsTime: String?): String {
            if (gtfsTime.isNullOrBlank()) return "N/A" // Return "N/A" for null or blank input.
            return try {
                // GTFS times can be > 24:00:00 (e.g., 25:30:00 for 1:30 AM next day).
                // SimpleDateFormat struggles with this directly. A simple string split is more robust for HH:MM display.
                val parts = gtfsTime.split(":") // Split the time string by colons.
                if (parts.size >= 2) {
                    // If we have at least hours and minutes, return them.
                    "${parts[0]}:${parts[1]}"
                } else {
                    // If not in the expected format, return the original string.
                    gtfsTime
                }
            } catch (e: Exception) {
                // In case of any other parsing exception, return the original string as a fallback.
                gtfsTime
            }
        }
    }

    /**
     * DiffUtil.ItemCallback for calculating the difference between two non-null items in a list.
     * Used by [ListAdapter] to efficiently update the RecyclerView.
     */
    class TicketDiffCallback : DiffUtil.ItemCallback<Ticket>() {
        /**
         * Called to check whether two objects represent the same item.
         * For example, if your items have unique IDs, this method should check their id equality.
         * @param oldItem The item in the old list.
         * @param newItem The item in the new list.
         * @return True if the two items represent the same object or false if they are different.
         */
        override fun areItemsTheSame(oldItem: Ticket, newItem: Ticket): Boolean {
            // Compare items based on their unique Firestore document ID.
            return oldItem.id == newItem.id
        }

        /**
         * Called to check whether two items have the same data.
         * This information is used to detect if the contents of an item have changed.
         * This method is called only if [areItemsTheSame] returns true for these items.
         * @param oldItem The item in the old list.
         * @param newItem The item in the new list.
         * @return True if the contents of the items are the same or false if they are different.
         */
        override fun areContentsTheSame(oldItem: Ticket, newItem: Ticket): Boolean {
            // Since Ticket is a data class, the '==' operator performs a structural comparison
            // of all its properties.
            return oldItem == newItem
        }
    }
}