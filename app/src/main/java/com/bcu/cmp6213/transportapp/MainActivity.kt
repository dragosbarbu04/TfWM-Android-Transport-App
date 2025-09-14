package com.bcu.cmp6213.transportapp

// AndroidX and App-specific imports
import android.content.Intent // Used for navigating between activities.
import android.os.Bundle // Used for saving and restoring activity state.
import android.util.Log // For logging messages.
import android.view.View // Base class for UI components.
import android.widget.Toast // For showing short messages to the user.
import androidx.activity.viewModels // For using the viewModels Kotlin property delegate to get ViewModel instances.
import androidx.appcompat.app.AppCompatActivity // Base class for activities that use the support library action bar features.
import androidx.appcompat.app.AppCompatDelegate // For controlling app theme (light/dark mode).
import androidx.fragment.app.Fragment // Base class for Fragments.
import androidx.lifecycle.Observer // To observe LiveData changes from ViewModels.

// App-specific imports for data binding, authentication, UI fragments, utilities, and ViewModels.
import com.bcu.cmp6213.transportapp.databinding.ActivityMainBinding // ViewBinding class for activity_main.xml.
import com.bcu.cmp6213.transportapp.auth.LoginActivity // Activity for user login.
import com.bcu.cmp6213.transportapp.ui.map.MapFragment // Fragment for displaying the map and route planning.
import com.bcu.cmp6213.transportapp.ui.profile.ProfileFragment // Fragment for user profile and settings.
import com.bcu.cmp6213.transportapp.routes.RoutesFragment // Fragment for displaying available GTFS routes.
import com.bcu.cmp6213.transportapp.ui.tickets.TicketsFragment // Fragment for displaying user's purchased tickets.
import com.bcu.cmp6213.transportapp.utils.SessionManager // Utility for managing user session and app preferences.
import com.bcu.cmp6213.transportapp.viewmodel.GtfsViewModel // Shared ViewModel for GTFS data.

/**
 * The main activity of the application, displayed after successful user login.
 * It hosts a [BottomNavigationView] to switch between different feature fragments
 * (Routes, Map, Tickets, Profile).
 * It also observes global states from [GtfsViewModel] like loading indicators and errors.
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding instance for accessing views in activity_main.xml.
    private lateinit var binding: ActivityMainBinding
    // Shared ViewModel instance, scoped to this Activity's lifecycle.
    // Used for accessing and managing GTFS data across different fragments.
    private val gtfsViewModel: GtfsViewModel by viewModels()

    // Companion object for constants, like the TAG for logging.
    companion object {
        private const val TAG = "MainActivity" // Log tag for this Activity.
    }

    /**
     * Called when the activity is first created.
     * Initializes the activity, applies theme preferences, checks login status,
     * sets up the BottomNavigationView, observes ViewModel states, and potentially
     * triggers an initial load of GTFS data.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     * this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Otherwise, it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Apply the saved theme preference (light/dark) before setting the content view.
        applyThemePreference()

        // Check if the user is logged in using SessionManager.
        // SessionManager.isLoggedIn() now synchronizes with Firebase's authentication state.
        if (!SessionManager.isLoggedIn(this)) {
            navigateToLogin() // If not logged in, redirect to LoginActivity.
            return // Exit onCreate to prevent further execution for an unauthenticated user.
        }

        // Inflate the layout using ViewBinding.
        binding = ActivityMainBinding.inflate(layoutInflater)
        // Set the content view to the root of the binding.
        setContentView(binding.root)

        // Set up the listener for item selection in the BottomNavigationView.
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            var selectedFragment: Fragment? = null // To hold the fragment to be displayed.
            var title: String = getString(R.string.app_name) // Default ActionBar title.

            // Determine which fragment to display based on the selected menu item ID.
            when (item.itemId) {
                R.id.navigation_routes -> {
                    selectedFragment = RoutesFragment.newInstance()
                    title = "Routes" // Set title for Routes screen.
                }
                R.id.navigation_map -> {
                    selectedFragment = MapFragment.newInstance()
                    title = "Map" // Set title for Map screen.
                }
                R.id.navigation_tickets -> { // Handle navigation to the new TicketsFragment.
                    selectedFragment = TicketsFragment.newInstance()
                    title = "My Tickets" // Set title for Tickets screen.
                }
                R.id.navigation_profile -> {
                    selectedFragment = ProfileFragment.newInstance()
                    title = "Profile" // Set title for Profile screen.
                }
            }

            // If a fragment was selected, replace the content of the FragmentContainerView.
            if (selectedFragment != null) {
                replaceFragment(selectedFragment)
                supportActionBar?.title = title // Update the ActionBar title.
            }
            true // Return true to indicate the selection event was handled.
        }

        // If savedInstanceState is null (e.g., on first launch or after process death without state save),
        // set a default selected item for the BottomNavigationView.
        if (savedInstanceState == null) {
            // Set the Map fragment as the default screen. This can be changed as per preference.
            binding.bottomNavigationView.selectedItemId = R.id.navigation_map
        }

        // Start observing global states (loading, errors) from the GtfsViewModel.
        observeGlobalViewModelStates()

        // Trigger an initial load of GTFS data if it seems like data is missing and
        // there's no active loading process or error. This ensures data is available
        // when the app starts after login.
        if (gtfsViewModel.filteredRoutes.value.isNullOrEmpty() &&
            gtfsViewModel.isLoading.value != true &&
            gtfsViewModel.error.value == null) {
            Log.d(TAG, "onCreate: Initial GTFS data load triggered from MainActivity as data seems empty and no active load/error.")
            // Load GTFS data, but don't force download if cached files are available and valid.
            gtfsViewModel.loadGtfsData(forceDownload = false)
        }
    }

    /**
     * Applies the theme preference (light/dark) stored in SessionManager.
     * This method is called early in onCreate to set the theme before UI inflation.
     */
    private fun applyThemePreference() {
        // Get the saved theme preference ("light", "dark", or null).
        val theme = SessionManager.getThemePreference(this)
        if (theme == "dark") {
            // If "dark" is preferred, set the night mode to YES.
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            // Otherwise (for "light" or no preference), set night mode to NO (light mode).
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    /**
     * Replaces the currently displayed fragment in the `nav_host_fragment_container`
     * with the provided [fragment].
     * @param fragment The [Fragment] to display.
     */
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment_container, fragment) // Replace content of the container.
            .commit() // Commit the transaction.
    }

    /**
     * Sets up observers for global LiveData states from the [GtfsViewModel],
     * such as overall loading status and general error messages.
     * This allows MainActivity to display global UI feedback.
     */
    private fun observeGlobalViewModelStates() {
        // Observe the global loading state from GtfsViewModel.
        gtfsViewModel.isLoading.observe(this, Observer { isLoading ->
            // Show or hide the main ProgressBar based on the loading state.
            binding.progressBarMain.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                Log.d(TAG, "observeGlobalViewModelStates: Global loading indicator shown (MainActivity).")
            } else {
                Log.d(TAG, "observeGlobalViewModelStates: Global loading indicator hidden (MainActivity).")
            }
        })

        // Observe general error messages from GtfsViewModel.
        gtfsViewModel.error.observe(this, Observer { errorMessage ->
            // If an error message is received, display it in a Toast and log it.
            errorMessage?.let {
                Toast.makeText(this, "Error: $it", Toast.LENGTH_LONG).show()
                Log.e(TAG, "observeGlobalViewModelStates: Global error observed in MainActivity: $it")
            }
        })
    }

    /**
     * Navigates the user to the [LoginActivity].
     * This is typically called if the user is not logged in or after logging out.
     * It clears the current activity stack to prevent returning to MainActivity via the back button.
     */
    private fun navigateToLogin() {
        // Create an Intent to start LoginActivity.
        val intent = Intent(this, LoginActivity::class.java)
        // Set flags to clear the existing task and create a new one for LoginActivity.
        // This ensures that pressing 'back' from LoginActivity will exit the app,
        // and pressing 'back' from MainActivity (if it wasn't finished) won't go to LoginActivity.
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish() // Finish MainActivity to remove it from the back stack.
    }
}