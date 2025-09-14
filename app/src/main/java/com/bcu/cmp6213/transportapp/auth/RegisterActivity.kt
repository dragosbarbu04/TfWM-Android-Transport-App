package com.bcu.cmp6213.transportapp.auth

// Import necessary Android classes for Activity, Intent, Bundle, logging, and UI elements.
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
// Import for enabling edge-to-edge display.
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
// Imports for handling window insets to avoid UI elements being obscured by system bars.
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
// Import for using coroutines tied to the Activity's lifecycle.
import androidx.lifecycle.lifecycleScope
// Import ViewBinding class for activity_register.xml.
import com.bcu.cmp6213.transportapp.databinding.ActivityRegisterBinding
// Import Firebase Authentication and Firestore classes.
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
// Import kotlinx.coroutines.launch for starting coroutines.
import kotlinx.coroutines.launch
// Import kotlinx.coroutines.tasks.await for a more idiomatic way to handle Firebase Tasks in coroutines.
import kotlinx.coroutines.tasks.await

/**
 * RegisterActivity provides the user interface and logic for new user registration.
 * It allows users to create an account using their email and password with Firebase Authentication,
 * and stores additional profile information (like name) in Firestore.
 */
class RegisterActivity : AppCompatActivity() {

    // ViewBinding variable to easily access views in the layout (e.g., EditTexts, Buttons).
    private lateinit var binding: ActivityRegisterBinding
    // Firebase Authentication instance.
    private lateinit var auth: FirebaseAuth
    // Firestore database instance for storing user profile data.
    private val db = Firebase.firestore

    // Companion object to hold constants, like the TAG for logging.
    companion object {
        private const val TAG = "RegisterActivity" // TAG for identifying log messages from this Activity.
    }

    /**
     * Called when the activity is first created.
     * This method initializes the activity, sets up Firebase services,
     * inflates the layout, handles window insets for edge-to-edge display,
     * and sets up UI listeners.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     * this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Otherwise, it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge display for a more immersive UI. This should be called early.
        enableEdgeToEdge()

        // Inflate the layout using ViewBinding.
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        // Set the content view to the root of the binding.
        setContentView(binding.root)

        // Initialize Firebase Auth instance.
        auth = Firebase.auth

        // Apply window insets listener to the root view to handle system bars (status bar, navigation bar)
        // in an edge-to-edge display. This adds padding to prevent UI overlap.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Set an OnClickListener for the registration button.
        // When clicked, it will call the performRegistration() method.
        binding.buttonRegister.setOnClickListener {
            performRegistration()
        }

        // Set an OnClickListener for the "Login" link (TextView).
        // When clicked, it navigates to LoginActivity and finishes the current RegisterActivity.
        binding.textViewLoginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish() // Finish RegisterActivity so user can't go back to it from LoginActivity via back press.
        }
    }

    /**
     * Handles the user registration process when the register button is clicked.
     * It validates user input, creates a new user with Firebase Authentication,
     * and then stores additional user profile information in Firestore.
     */
    private fun performRegistration() {
        // Get user input from EditText fields, trimming whitespace.
        val fullName = binding.editTextFullName.text.toString().trim()
        val email = binding.editTextEmailRegister.text.toString().trim()
        val password = binding.editTextPasswordRegister.text.toString()
        val confirmPassword = binding.editTextConfirmPasswordRegister.text.toString()

        // --- Input Validation ---
        // Check if essential fields (email, passwords) are empty.
        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Email and passwords cannot be empty", Toast.LENGTH_SHORT).show()
            return // Exit if validation fails.
        }

        // Validate email format.
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmailRegister.error = "Enter a valid email" // Show error in TextInputLayout.
            return
        } else {
            binding.tilEmailRegister.error = null // Clear any previous error.
        }

        // Validate password length (Firebase default minimum is 6 characters).
        if (password.length < 6) {
            binding.tilPasswordRegister.error = "Password must be at least 6 characters"
            return
        } else {
            binding.tilPasswordRegister.error = null
        }

        // Validate if password and confirm password fields match.
        if (password != confirmPassword) {
            binding.tilConfirmPasswordRegister.error = "Passwords do not match"
            return
        } else {
            binding.tilConfirmPasswordRegister.error = null
        }

        // Launch a coroutine within the Activity's lifecycle scope for Firebase operations.
        lifecycleScope.launch {
            try {
                // Create a new user account with Firebase Authentication using email and password.
                // .await() suspends the coroutine until the Firebase task completes.
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                // Get the FirebaseUser object from the authentication result.
                val firebaseUser = authResult.user
                Log.d(TAG, "createUserWithEmail:success, UID: ${firebaseUser?.uid}") // Log successful user creation.

                // If Firebase user creation was successful and we have a user object.
                if (firebaseUser != null) {
                    // Prepare user profile data to be stored in Firestore.
                    // Use fullName if provided, otherwise store null (or an empty string if preferred).
                    val userProfile = hashMapOf(
                        "name" to fullName.ifEmpty { null }, // Store full name, or null if empty.
                        "email" to email,                    // Store email.
                        "isPremium" to false                 // Default 'isPremium' status to false.
                    )

                    // Store the userProfile in a Firestore collection named "users".
                    // The document ID will be the Firebase User's UID, linking the profile to the auth account.
                    db.collection("users").document(firebaseUser.uid)
                        .set(userProfile)
                        .await() // Wait for Firestore write to complete.

                    Log.d(TAG, "User profile created in Firestore for UID: ${firebaseUser.uid}")
                    Toast.makeText(this@RegisterActivity, "Registration successful!", Toast.LENGTH_SHORT).show()

                    // Navigate to LoginActivity after successful registration.
                    startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                    finish() // Finish RegisterActivity.
                } else {
                    // This case is unlikely if createUserWithEmailAndPassword succeeds without an exception,
                    // but included for completeness.
                    Toast.makeText(this@RegisterActivity, "Registration completed but failed to get user details.", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                // Handle exceptions during Firebase user creation or Firestore write.
                // Examples: email already in use (FirebaseAuthUserCollisionException), network issues.
                Log.w(TAG, "createUserWithEmail:failure", e) // Log the exception.
                // Show a user-friendly error message.
                Toast.makeText(this@RegisterActivity, "Registration failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                // This block executes regardless of success or failure.
            }
        }
    }
}