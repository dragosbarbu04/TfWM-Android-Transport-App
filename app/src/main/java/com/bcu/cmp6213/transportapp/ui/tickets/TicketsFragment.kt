// File: com/bcu/cmp6213/transportapp/ui/tickets/TicketsFragment.kt
package com.bcu.cmp6213.transportapp.ui.tickets

// AndroidX and App-specific imports
import android.os.Bundle
import android.util.Log // For logging messages.
import androidx.fragment.app.Fragment // Base class for Fragment.
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast // For showing short messages to the user.
import androidx.fragment.app.viewModels // For using the viewModels Kotlin property delegate to get ViewModel instances.
import androidx.lifecycle.Observer // To observe LiveData changes from the ViewModel.
import androidx.recyclerview.widget.LinearLayoutManager // For arranging items in a vertical list in RecyclerView.
import com.bcu.cmp6213.transportapp.databinding.FragmentTicketsBinding // ViewBinding class for fragment_tickets.xml.

/**
 * A [Fragment] subclass responsible for displaying a list of tickets "purchased" by the current user.
 * It retrieves ticket data via [TicketsViewModel] and displays it using a RecyclerView
 * with [TicketDisplayAdapter].
 */
class TicketsFragment : Fragment() {

    // ViewBinding instance for accessing views in fragment_tickets.xml.
    // Nullable because the view hierarchy is only available between onCreateView and onDestroyView.
    private var _binding: FragmentTicketsBinding? = null
    // Non-null accessor for the binding. This property is only valid
    // between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    // ViewModel instance for this fragment, scoped to this fragment's lifecycle.
    // The `by viewModels()` delegate handles creation and retention of the ViewModel.
    private val ticketsViewModel: TicketsViewModel by viewModels()
    // Adapter for the RecyclerView that will display the list of Ticket objects.
    private lateinit var ticketDisplayAdapter: TicketDisplayAdapter

    // Companion object for constants and a factory method.
    companion object {
        private const val TAG = "TicketsFragment" // TAG for logging from this fragment.
        /**
         * Factory method to create a new instance of this fragment.
         * Useful if arguments need to be passed to the fragment upon creation.
         * @return A new instance of fragment TicketsFragment.
         */
        @JvmStatic
        fun newInstance() = TicketsFragment()
    }

    /**
     * Called to have the fragment instantiate its user interface view.
     * This is where the layout is inflated using ViewBinding.
     * @param inflater The LayoutInflater object that can be used to inflate any views in the fragment.
     * @param container If non-null, this is the parent view that the fragment's UI should be attached to.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state.
     * @return Return the View for the fragment's UI.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment using ViewBinding.
        _binding = FragmentTicketsBinding.inflate(inflater, container, false)
        // Return the root view of the inflated layout.
        return binding.root
    }

    /**
     * Called immediately after onCreateView() has returned, but before any saved state
     * has been restored in to the view.
     * This is where UI setup (RecyclerView) and ViewModel observation are done.
     * It also triggers the initial fetch of user tickets.
     * @param view The View returned by onCreateView().
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize the RecyclerView.
        setupRecyclerView()
        // Set up observers for LiveData from the TicketsViewModel.
        observeViewModel()

        // Request the ViewModel to fetch the user's tickets from Firestore.
        // This is typically done when the view is created to load initial data.
        ticketsViewModel.fetchUserTickets()
    }

    /**
     * Initializes the RecyclerView, including setting its LayoutManager and Adapter.
     */
    private fun setupRecyclerView() {
        // Create an instance of the TicketDisplayAdapter.
        ticketDisplayAdapter = TicketDisplayAdapter()
        // Configure the RecyclerView.
        binding.recyclerViewUserTickets.apply {
            // Set the LayoutManager that positions items in the list (vertically by default).
            layoutManager = LinearLayoutManager(requireContext())
            // Set the adapter for the RecyclerView.
            adapter = ticketDisplayAdapter
        }
    }

    /**
     * Sets up observers for LiveData objects exposed by the [TicketsViewModel].
     * This allows the UI to reactively update based on changes in data (tickets list),
     * loading state, or error messages.
     */
    private fun observeViewModel() {
        // Observe the LiveData list of tickets.
        ticketsViewModel.tickets.observe(viewLifecycleOwner, Observer { tickets ->
            // When the list of tickets changes, submit it to the adapter for display.
            // ListAdapter will efficiently calculate differences and update the UI.
            ticketDisplayAdapter.submitList(tickets)

            // Manage visibility of "No Tickets" message and RecyclerView based on data.
            if (tickets.isNullOrEmpty() && ticketsViewModel.isLoading.value == false && ticketsViewModel.error.value == null) {
                // If the list is empty, not currently loading, and no error, show "No Tickets" message.
                binding.textViewNoTickets.visibility = View.VISIBLE
                binding.recyclerViewUserTickets.visibility = View.GONE
            } else if (!tickets.isNullOrEmpty()){
                // If the list is not empty, hide "No Tickets" message and show the RecyclerView.
                binding.textViewNoTickets.visibility = View.GONE
                binding.recyclerViewUserTickets.visibility = View.VISIBLE
            }
            // Note: The error observer below also handles visibility if an error occurs.
        })

        // Observe the loading state from the ViewModel.
        ticketsViewModel.isLoading.observe(viewLifecycleOwner, Observer { isLoading ->
            // Show or hide the ProgressBar based on the loading state.
            binding.progressBarTickets.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                // While loading, hide both the "No Tickets" message and the RecyclerView.
                binding.textViewNoTickets.visibility = View.GONE
                binding.recyclerViewUserTickets.visibility = View.GONE
            }
            // When loading finishes, the `tickets` or `error` observers will update UI visibility.
        })

        // Observe error messages from the ViewModel.
        ticketsViewModel.error.observe(viewLifecycleOwner, Observer { errorMsg ->
            if (errorMsg != null) {
                // If an error message is received:
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show() // Show error in a Toast.
                Log.e(TAG, "Error observed in TicketsFragment: $errorMsg") // Log the error.
                binding.textViewNoTickets.text = "Error loading tickets." // Update TextView to show error.
                binding.textViewNoTickets.visibility = View.VISIBLE // Make error TextView visible.
                binding.recyclerViewUserTickets.visibility = View.GONE // Hide RecyclerView.
                ticketDisplayAdapter.submitList(emptyList()) // Clear any existing data from the adapter.
            } else {
                // If errorMsg is null (e.g., error has been cleared):
                // Check if tickets are still empty and not loading, then show "You have no tickets."
                // This ensures the "No Tickets" message is correctly displayed if an error state is resolved
                // but there are still no tickets.
                if (ticketsViewModel.tickets.value.isNullOrEmpty() && ticketsViewModel.isLoading.value == false) {
                    binding.textViewNoTickets.text = "You have no tickets."
                    binding.textViewNoTickets.visibility = View.VISIBLE
                }
            }
        })
    }

    /**
     * Called when the view previously created by onCreateView() has been detached from the fragment.
     * The next time the fragment needs to be displayed, a new view will be created.
     * This is where we clean up the ViewBinding instance (_binding) to prevent memory leaks.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        // Nullify the binding object when the view is destroyed. This is crucial for fragments
        // using ViewBinding to avoid holding onto references to the view hierarchy,
        // which can lead to memory leaks.
        _binding = null
    }
}