package com.bcu.cmp6213.transportapp.ui.profile

// AndroidX and App-specific imports
import android.content.Intent
import android.os.Bundle
import android.util.Log // For logging
import androidx.fragment.app.Fragment // Base class for Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button // For the logout button
import android.widget.TextView // For displaying user information
import android.widget.Toast // For showing short messages
import androidx.appcompat.app.AppCompatDelegate // For controlling app theme (light/dark mode)
import com.bcu.cmp6213.transportapp.R // For accessing resources like layout IDs
import com.bcu.cmp6213.transportapp.auth.LoginActivity // To navigate to login screen on logout
import com.bcu.cmp6213.transportapp.utils.SessionManager // For managing theme preference and user session
import com.google.android.material.materialswitch.MaterialSwitch // Modern switch component for theme toggle
// Firebase imports
import com.google.firebase.auth.ktx.auth // Firebase Authentication
import com.google.firebase.firestore.ktx.firestore // Firebase Firestore
import com.google.firebase.ktx.Firebase // Base Firebase import

/**
 * A [Fragment] subclass that displays user profile information and settings.
 * It allows users to:
 * - View their name (fetched from Firestore) and email.
 * - Toggle between light and dark application themes.
 * - Log out of the application.
 *
 * Note: This fragment currently uses `findViewById` to access its views.
 * For consistency with other fragments like `MapFragment` and `RoutesFragment`
 * which use ViewBinding, consider refactoring this fragment to use ViewBinding as well.
 */
class ProfileFragment : Fragment() {

    // UI element to display the user's name and email.
    private lateinit var textViewUserName: TextView
    // Firestore database instance for fetching user profile data.
    private val db = Firebase.firestore

    // Companion object for constants and a factory method.
    companion object {
        private const val TAG = "ProfileFragment" // TAG for logging from this fragment.
        /**
         * Factory method to create a new instance of this fragment.
         * @return A new instance of fragment ProfileFragment.
         */
        @JvmStatic
        fun newInstance() = ProfileFragment()
    }

    /**
     * Called to have the fragment instantiate its user interface view.
     * This is where the layout is inflated and view references are obtained.
     * @param inflater The LayoutInflater object that can be used to inflate any views in the fragment.
     * @param container If non-null, this is the parent view that the fragment's UI should be attached to.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state.
     * @return Return the View for the fragment's UI, or null.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment.
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Initialize UI elements by finding them in the inflated view.
        textViewUserName = view.findViewById<TextView>(R.id.textViewUserNameProfile)
        val switchTheme = view.findViewById<MaterialSwitch>(R.id.switchTheme)
        val buttonLogout = view.findViewById<Button>(R.id.buttonLogoutProfile)

        // --- Display User Information ---
        // Get the current Firebase authenticated user.
        val currentUser = Firebase.auth.currentUser
        // Get user's email either directly from Firebase or from SessionManager cache as a fallback.
        val userEmail = currentUser?.email ?: SessionManager.getLoggedInUserEmail(requireContext())
        // Set initial text for user info display (will be updated with name if fetched successfully).
        textViewUserName.text = "Email: ${userEmail ?: "N/A"}"

        // Asynchronously fetch the user's full name from Firestore and update the display.
        fetchAndDisplayUserName()


        // --- Theme Switch Logic ---
        // Get the currently applied night mode setting for the app.
        val currentNightMode = AppCompatDelegate.getDefaultNightMode()
        // Set the switch to checked if dark mode (MODE_NIGHT_YES) is currently active.
        switchTheme.isChecked = currentNightMode == AppCompatDelegate.MODE_NIGHT_YES

        // Set a listener for changes in the theme switch's checked state.
        switchTheme.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // If the switch is checked, enable dark mode.
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                // Save the "dark" theme preference using SessionManager.
                SessionManager.saveThemePreference(requireContext(), "dark")
            } else {
                // If the switch is unchecked, disable dark mode (enable light mode).
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                // Save the "light" theme preference using SessionManager.
                SessionManager.saveThemePreference(requireContext(), "light")
            }
            // Inform the user about the theme change. A restart might be needed for some UI elements
            // to fully update, though AppCompatDelegate tries to apply changes immediately where possible.
            Toast.makeText(requireContext(), "Theme changed. May require app restart.", Toast.LENGTH_SHORT).show()
        }

        // --- Logout Button Logic ---
        // Set an OnClickListener for the logout button.
        buttonLogout.setOnClickListener {
            // Call SessionManager.logoutUser() which handles Firebase sign-out
            // and clears relevant SharedPreferences data.
            SessionManager.logoutUser(requireContext())
            // Create an Intent to navigate back to LoginActivity.
            val intent = Intent(activity, LoginActivity::class.java)
            // Set flags to clear the activity stack, so the user cannot navigate back
            // to MainActivity or ProfileFragment after logging out.
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            // Finish the hosting activity (MainActivity) to remove it from the back stack.
            activity?.finish()
        }

        // Return the inflated view for the fragment.
        return view
    }

    /**
     * Fetches the current user's profile (specifically their name) from Firestore
     * and updates the `textViewUserName` to display it along with their email.
     */
    private fun fetchAndDisplayUserName() {
        // Get the currently authenticated Firebase user.
        val firebaseUser = Firebase.auth.currentUser
        if (firebaseUser != null) {
            // If a user is logged in, get their UID.
            val uid = firebaseUser.uid
            // Access the "users" collection in Firestore and get the document corresponding to the user's UID.
            db.collection("users").document(uid)
                .get()
                .addOnSuccessListener { document ->
                    // Handle successful document retrieval.
                    if (document != null && document.exists()) {
                        // If the document exists, attempt to get the "name" and "email" fields.
                        val name = document.getString("name")
                        val email = document.getString("email") // This email is from Firestore.

                        // Update the TextView with the fetched name and email.
                        // Prioritize showing the name if available.
                        if (!name.isNullOrBlank()) {
                            textViewUserName.text = "Name: $name\nEmail: ${email ?: firebaseUser.email ?: "N/A"}"
                        } else {
                            // If name is not set in Firestore, show email and a note.
                            textViewUserName.text = "Email: ${email ?: firebaseUser.email ?: "N/A"} (Name not set)"
                        }
                        Log.d(TAG, "User profile fetched from Firestore: Name - $name")
                    } else {
                        // Document does not exist in Firestore for this user.
                        Log.d(TAG, "No such user profile document in Firestore for UID: $uid.")
                        // textViewUserName is already set to display the email from Firebase Auth as a fallback.
                    }
                }
                .addOnFailureListener { exception ->
                    // Handle failure to retrieve the document from Firestore.
                    Log.w(TAG, "Error getting user profile document from Firestore:", exception)
                    Toast.makeText(requireContext(), "Failed to load profile name.", Toast.LENGTH_SHORT).show()
                }
        } else {
            // This case should ideally not be reached if the user is viewing this fragment,
            // as MainActivity checks for login status. But included for robustness.
            Log.w(TAG, "Cannot fetch user name, Firebase user is null.")
        }
    }
}