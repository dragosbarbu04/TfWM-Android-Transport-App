package com.bcu.cmp6213.transportapp.auth

// Import necessary Android classes for Activity, Intent, Bundle, logging, and UI elements.
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope // Import for using coroutines tied to the Activity's lifecycle.

// Import ViewBinding class for activity_login.xml.
import com.bcu.cmp6213.transportapp.databinding.ActivityLoginBinding
// Import MainActivity to navigate to after successful login.
import com.bcu.cmp6213.transportapp.MainActivity
// Import SessionManager utility for handling user login sessions.
import com.bcu.cmp6213.transportapp.utils.SessionManager
// Import Firebase Authentication classes.
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
// Import kotlinx.coroutines.launch for starting coroutines.
import kotlinx.coroutines.launch
// Import kotlinx.coroutines.tasks.await for a more idiomatic way to handle Firebase Tasks in coroutines.
import kotlinx.coroutines.tasks.await

/**
 * LoginActivity provides the user interface and logic for user authentication.
 * It allows users to log in using their email and password with Firebase Authentication.
 */
class LoginActivity : AppCompatActivity() {

    // ViewBinding variable to easily access views in the layout.
    private lateinit var binding: ActivityLoginBinding
    // Firebase Authentication instance.
    private lateinit var auth: FirebaseAuth

    // Companion object to hold constants, like the TAG for logging.
    companion object {
        private const val TAG = "LoginActivity" // TAG for identifying log messages from this Activity.
    }

    /**
     * Called when the activity is first created.
     * This method initializes the activity, sets up Firebase Auth,
     * checks if the user is already logged in, and sets up UI listeners.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     * this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Otherwise, it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Firebase Auth instance. This should be done early.
        auth = Firebase.auth

        // Check if the user is already logged in via SessionManager (which now syncs with Firebase state).
        // If logged in, navigate directly to MainActivity and finish this activity.
        if (SessionManager.isLoggedIn(this)) {
            Log.d(TAG, "User already logged in (checked via SessionManager synchronized with Firebase). UID: ${SessionManager.getLoggedInUserUid(this)}")
            navigateToMain()
            // Return is important here to prevent the rest of onCreate (like setContentView)
            // from executing if the user is already logged in and navigated away.
            return
        }

        // If not logged in, proceed to inflate the layout using ViewBinding.
        binding = ActivityLoginBinding.inflate(layoutInflater)
        // Set the content view to the root of the binding.
        setContentView(binding.root)

        // Set an OnClickListener for the login button.
        // When clicked, it will call the performLogin() method.
        binding.buttonLogin.setOnClickListener {
            performLogin()
        }

        // Set an OnClickListener for the "Register" link (TextView).
        // When clicked, it navigates to RegisterActivity.
        binding.textViewRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Placeholder for "Forgot Password" functionality.
        // This can be implemented later if required.
        // binding.textViewForgotPassword.setOnClickListener {
        //     Toast.makeText(this, "Forgot Password clicked - Implement later", Toast.LENGTH_SHORT).show()
        // }
    }

    /**
     * Handles the user login process when the login button is clicked.
     * It validates user input and attempts to sign in with Firebase Authentication.
     */
    private fun performLogin() {
        // Get email and password from the EditText fields, trimming whitespace.
        val email = binding.editTextEmailLogin.text.toString().trim()
        val password = binding.editTextPasswordLogin.text.toString()

        // Validate that email and password fields are not empty.
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email and password cannot be empty", Toast.LENGTH_SHORT).show()
            return // Exit the function if validation fails.
        }

        // Validate the email format using Android's built-in email pattern matcher.
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmailLogin.error = "Enter a valid email address" // Show error in TextInputLayout.
            return // Exit the function if email format is invalid.
        } else {
            binding.tilEmailLogin.error = null // Clear any previous error.
        }

        // Launch a coroutine within the Activity's lifecycle scope for the Firebase Authentication call.
        // This ensures the operation is lifecycle-aware and doesn't cause issues if the Activity is destroyed.
        lifecycleScope.launch {
            try {
                // Attempt to sign in the user with Firebase Authentication using email and password.
                // .await() suspends the coroutine until the Firebase task completes, returning the result or throwing an exception.
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                // Get the FirebaseUser object from the authentication result.
                val firebaseUser = authResult.user
                Log.d(TAG, "signInWithEmail:success, UID: ${firebaseUser?.uid}") // Log successful sign-in.

                // Check if the FirebaseUser object is not null (it should be on success).
                if (firebaseUser != null) {
                    // Login successful.
                    // Create a user session using SessionManager, storing UID and email.
                    // The email might be null from Firebase in some rare cases, so provide a fallback.
                    SessionManager.createLoginSession(this@LoginActivity, firebaseUser.uid, firebaseUser.email ?: "Email not available")
                    Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                    // Navigate to the MainActivity.
                    navigateToMain()
                } else {
                    // This case is unlikely if signInWithEmailAndPassword succeeds without throwing an exception,
                    // but included for robustness.
                    Toast.makeText(this@LoginActivity, "Login completed but failed to get user details.", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                // Handle any exceptions during the Firebase sign-in process (e.g., wrong password, user not found, network error).
                Log.w(TAG, "signInWithEmail:failure", e) // Log the exception.
                // Show a user-friendly error message. e.message often provides a good summary from Firebase.
                Toast.makeText(this@LoginActivity, "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                // This block will always execute, whether the try block succeeds or fails.
            }
        }
    }

    /**
     * Navigates the user to the MainActivity after a successful login.
     * It also clears the activity stack so the user cannot go back to the LoginActivity.
     */
    private fun navigateToMain() {
        // Create an Intent to start MainActivity.
        val intent = Intent(this, MainActivity::class.java)
        // Set flags to clear the task and start a new one. This ensures that if the user
        // presses the back button from MainActivity, they will exit the app instead of
        // returning to LoginActivity.
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        // Finish LoginActivity to remove it from the back stack.
        finish()
    }
}