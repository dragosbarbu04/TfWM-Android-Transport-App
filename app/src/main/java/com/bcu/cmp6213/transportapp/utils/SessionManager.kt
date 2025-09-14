package com.bcu.cmp6213.transportapp.utils

// Android and Firebase imports
import android.content.Context // Interface to global information about an application environment.
import android.content.SharedPreferences // Interface for accessing and modifying preference data returned by getSharedPreferences().
import android.util.Log // For logging.
import com.google.firebase.auth.ktx.auth // Firebase Authentication Kotlin extensions.
import com.google.firebase.ktx.Firebase // Base Firebase import.

/**
 * A singleton object responsible for managing user session data and app preferences
 * using SharedPreferences.
 *
 * It handles:
 * - Creating and clearing login sessions (synchronized with Firebase Authentication).
 * - Checking if a user is currently logged in.
 * - Storing and retrieving basic user information like UID and email.
 * - Storing and retrieving app-level preferences like the selected theme.
 */
object SessionManager {
    // Name of the SharedPreferences file.
    private const val PREFS_NAME = "TransportAppPrefs"

    // Keys for storing session-related data in SharedPreferences.
    private const val KEY_IS_LOGGED_IN = "isLoggedIn" // Boolean: true if user is considered logged in by the app.
    private const val KEY_USER_UID = "userUid"        // String: Stores the Firebase User ID (UID).
    private const val KEY_USER_EMAIL = "userEmail"    // String: Stores the user's email.

    // Key for storing the application theme preference.
    private const val KEY_APP_THEME = "appTheme"      // String: e.g., "light" or "dark".

    /**
     * Private helper function to get the SharedPreferences instance.
     * @param context The context from which to access SharedPreferences.
     * @return The SharedPreferences instance for this application.
     */
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Creates a login session in SharedPreferences after successful Firebase authentication.
     * Stores the logged-in status, Firebase User UID, and email.
     * @param context The context used to access SharedPreferences.
     * @param uid The Firebase User ID (UID) of the logged-in user.
     * @param email The email address of the logged-in user.
     */
    fun createLoginSession(context: Context, uid: String, email: String) {
        val editor = getPreferences(context).edit() // Get SharedPreferences editor.
        editor.putBoolean(KEY_IS_LOGGED_IN, true) // Set logged-in flag to true.
        editor.putString(KEY_USER_UID, uid)       // Store user UID.
        editor.putString(KEY_USER_EMAIL, email)   // Store user email.
        // Note: You could also store other non-sensitive user profile data here if needed
        // for quick access, e.g., user's full name fetched from Firestore after login.
        // editor.putString("userFullName", fullName)
        editor.apply() // Apply changes asynchronously.
        Log.d("SessionManager", "Login session created for UID: $uid, Email: $email")
    }

    /**
     * Checks if the user is currently logged in.
     * This method primarily relies on the Firebase Authentication state (`Firebase.auth.currentUser`).
     * It also attempts to synchronize the SharedPreferences `isLoggedIn` flag with the Firebase state.
     * @param context The context used to access SharedPreferences.
     * @return True if the user is logged in (Firebase session active), false otherwise.
     */
    fun isLoggedIn(context: Context): Boolean {
        val firebaseUser = Firebase.auth.currentUser // Get the current Firebase user.

        if (firebaseUser != null) {
            // If Firebase reports a user is logged in:
            // Check if our SharedPreferences state is consistent. If not, re-sync it.
            // This handles cases where SharedPreferences might have been cleared or become stale
            // while the Firebase session persisted (e.g., app data cleared by user).
            if (!getPreferences(context).getBoolean(KEY_IS_LOGGED_IN, false) ||
                getPreferences(context).getString(KEY_USER_UID, null) != firebaseUser.uid) {
                Log.d("SessionManager", "isLoggedIn: Firebase user found, re-syncing SharedPreferences session.")
                // Re-create the session in SharedPreferences using details from the current Firebase user.
                // Provide a fallback empty string if email is null from Firebase (though unlikely for email/password auth).
                createLoginSession(context, firebaseUser.uid, firebaseUser.email ?: "")
            }
            return true // User is logged in according to Firebase.
        }

        // If Firebase reports no user is logged in:
        // Check if SharedPreferences still incorrectly indicates a logged-in state (inconsistency).
        if (getPreferences(context).getBoolean(KEY_IS_LOGGED_IN, false)) {
            Log.w("SessionManager", "isLoggedIn: Firebase user is null, but SharedPreferences indicates logged in. Clearing inconsistent SP session.")
            // If SharedPreferences is out of sync, clear its session data.
            clearSessionForLogout(context)
        }
        return false // User is not logged in.
    }

    /**
     * Retrieves the stored Firebase User ID (UID) of the logged-in user from SharedPreferences.
     * @param context The context used to access SharedPreferences.
     * @return The user's UID as a String, or null if not logged in or UID not found.
     */
    fun getLoggedInUserUid(context: Context): String? {
        return getPreferences(context).getString(KEY_USER_UID, null)
    }

    /**
     * Retrieves the stored email of the logged-in user from SharedPreferences.
     * @param context The context used to access SharedPreferences.
     * @return The user's email as a String, or null if not logged in or email not found.
     */
    fun getLoggedInUserEmail(context: Context): String? {
        return getPreferences(context).getString(KEY_USER_EMAIL, null)
    }

    /**
     * Private helper function to clear session-specific data from SharedPreferences.
     * This is called during logout or when an inconsistent session state is detected.
     * It specifically removes session keys, leaving other preferences (like theme) intact.
     * @param context The context used to access SharedPreferences.
     */
    private fun clearSessionForLogout(context: Context) {
        val editor = getPreferences(context).edit()
        editor.remove(KEY_IS_LOGGED_IN) // Remove logged-in flag.
        editor.remove(KEY_USER_UID)     // Remove user UID.
        editor.remove(KEY_USER_EMAIL)   // Remove user email.
        // If other session-specific data (like a cached full name) was stored, remove it here too.
        // e.g., editor.remove("userFullName")
        // Note: Theme preference (KEY_APP_THEME) is NOT cleared here by default,
        // as theme is often a persistent app setting rather than session-specific.
        editor.apply() // Apply changes.
        Log.d("SessionManager", "SharedPreferences session data cleared for logout.")
    }

    /**
     * Logs out the user from Firebase Authentication and clears the local session data
     * stored in SharedPreferences.
     * @param context The context used to access SharedPreferences.
     */
    fun logoutUser(context: Context) {
        Firebase.auth.signOut() // Sign the user out from Firebase Authentication.
        clearSessionForLogout(context) // Clear the session data from SharedPreferences.
        Log.d("SessionManager", "User logged out from Firebase and local session cleared.")
    }

    // --- Theme Preferences ---

    /**
     * Saves the user's theme preference (e.g., "light" or "dark") to SharedPreferences.
     * @param context The context used to access SharedPreferences.
     * @param themeName The name of the theme to save (e.g., "light", "dark").
     */
    fun saveThemePreference(context: Context, themeName: String) {
        val editor = getPreferences(context).edit()
        editor.putString(KEY_APP_THEME, themeName)
        editor.apply()
        Log.d("SessionManager", "Theme preference saved: $themeName")
    }

    /**
     * Retrieves the saved theme preference from SharedPreferences.
     * @param context The context used to access SharedPreferences.
     * @return The saved theme name as a String (e.g., "light", "dark"),
     * or null if no theme preference has been saved yet.
     */
    fun getThemePreference(context: Context): String? {
        return getPreferences(context).getString(KEY_APP_THEME, null) // Returns null if not found.
    }
}