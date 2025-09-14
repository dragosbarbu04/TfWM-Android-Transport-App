package com.bcu.cmp6213.transportapp.data // Or your preferred package

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a User entity for the local Room database.
 *
 * !!! IMPORTANT NOTE ON CURRENT USAGE !!!
 * This `User` entity was originally designed for local user authentication using Room.
 * However, user authentication has since been migrated to Firebase Authentication.
 * Therefore, this Room entity is NO LONGER USED for storing user credentials (username, passwordHash)
 * for active login/registration processes.
 *
 * This entity and its associated `UserDao` might be:
 * 1. Deprecated and pending removal if all user profile data is solely managed in Firestore.
 * 2. Repurposed to cache non-sensitive user profile information (like name, isPremium status)
 * locally, fetched from Firestore, and keyed by the Firebase UID (which would require
 * schema changes, e.g., replacing 'id' or 'username' with Firebase UID as the primary key
 * for this cached data).
 *
 * The comments below describe the original intent of the fields for Room-based auth.
 *
 * This class is annotated with `@Entity` to mark it as a table in the Room database.
 * The table name is specified as "users".
 */
@Entity(tableName = "users")
data class User(
    /**
     * The primary key for the users table.
     * Annotated with `@PrimaryKey(autoGenerate = true)`, so Room will automatically
     * generate a unique integer ID for each new user record.
     * Defaulted to 0, as Room handles the actual ID generation.
     */
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /**
     * Stores the username, which was typically the user's email address for login.
     * Annotated with `@ColumnInfo` to specify the column name in the database table.
     *
     * --- CURRENT STATUS ---
     * For Firebase Authentication, the email is managed by Firebase. This field,
     * if this entity were to be used for caching, might store the email fetched from Firebase/Firestore.
     */
    @ColumnInfo(name = "username")
    val username: String,

    /**
     * Stores the hashed version of the user's password.
     * IMPORTANT: Originally intended to store a hash (e.g., SHA-256) and NOT plain text passwords.
     *
     * --- CURRENT STATUS ---
     * Password storage and verification are now handled securely by Firebase Authentication.
     * This field is no longer used for authentication. Storing password hashes locally
     * alongside Firebase Auth is redundant and not recommended.
     */
    @ColumnInfo(name = "password_hash")
    val passwordHash: String,

    /**
     * Optional field to store the user's full name.
     * Nullable if the name is not provided.
     *
     * --- CURRENT STATUS ---
     * User's full name is now primarily stored in Firestore, associated with their Firebase UID.
     * If this Room entity is used for caching, this field could cache that name.
     */
    @ColumnInfo(name = "name")
    val name: String? = null,

    /**
     * Optional boolean field, potentially to indicate if the user has a premium subscription status.
     * Defaults to `false`.
     *
     * --- CURRENT STATUS ---
     * Premium status or similar user-specific flags would typically be managed in Firestore
     * alongside other profile data. This field could cache that status if needed locally.
     */
    @ColumnInfo(name = "is_premium")
    val isPremium: Boolean = false

    // Additional fields for user preferences (e.g., preferred_theme) could have been added here
    // if local preference storage via Room was desired, though SharedPreferences is also common for themes.
    // e.g., @ColumnInfo(name = "preferred_theme") val preferredTheme: String? = null
)

// Note: This class is not currently Parcelable. If User objects (representing cached profiles)
// were ever needed to be passed between Android components like Activities or Fragments,
// it would need to implement the Parcelable interface (e.g., using @kotlinx.parcelize.Parcelize).