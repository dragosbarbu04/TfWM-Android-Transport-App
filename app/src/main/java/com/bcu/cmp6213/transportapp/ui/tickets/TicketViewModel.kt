// File: com/bcu/cmp6213/transportapp/ui/tickets/TicketsViewModel.kt
package com.bcu.cmp6213.transportapp.ui.tickets

// Android and Firebase imports
import android.util.Log // For logging.
import androidx.lifecycle.LiveData // Data holder class that can be observed within a given lifecycle.
import androidx.lifecycle.MutableLiveData // LiveData that can be modified.
import androidx.lifecycle.ViewModel // Base class for ViewModels.
import androidx.lifecycle.viewModelScope // Coroutine scope tied to the ViewModel's lifecycle.
import com.bcu.cmp6213.transportapp.data.Ticket // Data class representing a purchased ticket.
import com.google.firebase.auth.ktx.auth // Firebase Authentication Kotlin extensions.
import com.google.firebase.firestore.Query // For Firestore query features like ordering.
import com.google.firebase.firestore.ktx.firestore // Firebase Firestore Kotlin extensions.
import com.google.firebase.firestore.ktx.toObjects // Extension to convert Firestore snapshots to Kotlin objects.
import com.google.firebase.ktx.Firebase // Base Firebase import.
import kotlinx.coroutines.launch // Builder for launching new coroutines.
import kotlinx.coroutines.tasks.await // For more idiomatic handling of Firebase Tasks in coroutines.

/**
 * [ViewModel] for the [TicketsFragment].
 * This ViewModel is responsible for fetching and managing the list of tickets
 * "purchased" by the currently logged-in user from Firestore.
 * It exposes this data via [LiveData] to be observed by the UI.
 */
class TicketsViewModel : ViewModel() {

    // Instance of Firebase Firestore for database operations.
    private val db = Firebase.firestore
    // Instance of Firebase Authentication to get the current user.
    private val auth = Firebase.auth

    // --- LiveData properties exposed to the UI (TicketsFragment) ---

    // Private MutableLiveData to hold the list of tickets. This is modified within the ViewModel.
    private val _tickets = MutableLiveData<List<Ticket>>()
    // Public immutable LiveData that the UI can observe for changes to the list of tickets.
    val tickets: LiveData<List<Ticket>> = _tickets

    // Private MutableLiveData to hold the loading state (e.g., while fetching tickets).
    private val _isLoading = MutableLiveData<Boolean>()
    // Public immutable LiveData for observing the loading state.
    val isLoading: LiveData<Boolean> = _isLoading

    // Private MutableLiveData to hold any error messages that occur during data fetching. Nullable.
    private val _error = MutableLiveData<String?>()
    // Public immutable LiveData for observing error messages.
    val error: LiveData<String?> = _error

    // Companion object for constants, such as the TAG for logging.
    companion object {
        private const val TAG = "TicketsViewModel" // Log tag for this ViewModel.
    }

    /**
     * Fetches the list of tickets belonging to the currently authenticated user from Firestore.
     * - Queries the "tickets" collection.
     * - Filters by the current user's UID.
     * - Orders tickets by purchase timestamp in descending order (newest first).
     * - Updates LiveData for tickets, loading state, and errors.
     */
    fun fetchUserTickets() {
        // Get the UID of the currently logged-in Firebase user.
        val userId = auth.currentUser?.uid
        // If no user is logged in, post an error and an empty list of tickets.
        if (userId == null) {
            _error.value = "User not logged in. Cannot fetch tickets."
            _tickets.value = emptyList() // Ensure UI shows no tickets.
            _isLoading.value = false // Not loading if no user.
            return
        }

        // Set loading state to true and clear any previous errors.
        _isLoading.value = true
        _error.value = null

        // Launch a coroutine within the ViewModel's scope for the asynchronous Firestore query.
        // This ensures the operation is lifecycle-aware.
        viewModelScope.launch {
            try {
                // Construct and execute the Firestore query:
                // - From the "tickets" collection.
                // - Where the "userId" field matches the current user's UID.
                // - Order the results by "purchaseTimestamp" in descending order (newest tickets first).
                // - .await() suspends the coroutine until the get() operation completes.
                val snapshot = db.collection("tickets")
                    .whereEqualTo("userId", userId)
                    .orderBy("purchaseTimestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()

                // Convert the query snapshot documents into a list of Ticket objects.
                val userTickets = snapshot.toObjects<Ticket>()
                // Post the fetched list of tickets to the LiveData.
                _tickets.postValue(userTickets)

                // Log information about the fetched tickets.
                if (userTickets.isEmpty()) {
                    Log.d(TAG, "fetchUserTickets: No tickets found for user $userId")
                } else {
                    Log.d(TAG, "fetchUserTickets: Fetched ${userTickets.size} tickets for user $userId")
                }
            } catch (e: Exception) {
                // Handle any exceptions during the Firestore query (e.g., network error, permission denied).
                Log.e(TAG, "fetchUserTickets: Error fetching tickets for user $userId", e)
                // Post an error message to the LiveData.
                _error.postValue("Failed to load tickets: ${e.message}")
                // Post an empty list to the tickets LiveData to clear any old data on error.
                _tickets.postValue(emptyList())
            } finally {
                // This block always executes, whether the try block succeeded or failed.
                // Set loading state to false once the operation is complete.
                _isLoading.postValue(false)
            }
        }
    }
}