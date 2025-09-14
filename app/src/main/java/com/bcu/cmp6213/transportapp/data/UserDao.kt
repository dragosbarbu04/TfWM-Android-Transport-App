package com.bcu.cmp6213.transportapp.data // Or your preferred package

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Data Access Object (DAO) for the [User] entity in the Room database.
 *
 * This interface defines the methods for interacting with the `users` table.
 * Room will generate the necessary implementation code for these methods.
 *
 * !!! IMPORTANT NOTE ON CURRENT USAGE !!!
 * With the migration of user authentication to Firebase Authentication, the methods in this DAO
 * primarily related to user credential management (e.g., `insertUser` for storing new users
 * with password hashes, `getUserByUsername` for retrieving users for login) are
 * NO LONGER USED for the primary authentication flow.
 *
 * This DAO and the associated `User` entity might be:
 * 1. Deprecated and pending removal if all user profile data is exclusively managed in Firestore.
 * 2. Repurposed if the `User` entity is adapted to cache user profile information from Firestore.
 * This would likely involve changing query parameters (e.g., querying by Firebase UID instead
 * of the local auto-generated `id` or `username` for profile data).
 *
 * The comments below describe the original intent of each method within a Room-based
 * authentication system. All methods are `suspend` functions, designed to be called
 * from Kotlin Coroutines to perform database operations off the main thread.
 */
@Dao // Marks this interface as a Data Access Object for Room.
interface UserDao {

    /**
     * Inserts a new user into the `users` table.
     * The `onConflict` strategy is set to `IGNORE`, meaning if a user with the same
     * primary key (the auto-generated `id`) already exists, the insert operation will be ignored.
     * Note: For unique usernames, a separate check (e.g., `countUsersWithUsername`)
     * was typically performed before insertion in a local auth system.
     *
     * --- CURRENT STATUS ---
     * User creation is now handled by Firebase Authentication. This method is not used for
     * registering new users with credentials. It might be adapted if caching Firestore user
     * profiles in Room.
     *
     * @param user The [User] object to insert.
     * @return The row ID of the newly inserted user, or -1 if the insertion was ignored due to conflict.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUser(user: User): Long

    /**
     * Retrieves a user from the `users` table based on their username (email).
     * Limits the result to 1 user, as usernames were intended to be unique.
     *
     * --- CURRENT STATUS ---
     * User lookup for login authentication is now handled by Firebase Authentication.
     * This method is not used in the current Firebase-based login flow.
     *
     * @param username The username (email) of the user to retrieve.
     * @return The [User] object if found, or `null` if no user with that username exists.
     */
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    /**
     * Retrieves a user from the `users` table based on their auto-generated Room ID.
     *
     * --- CURRENT STATUS ---
     * This method uses Room's auto-generated integer `id`. If caching Firestore user profiles
     * (which are identified by a String Firebase UID), this method would need to be adapted
     * or a new method created (e.g., `getUserByFirebaseUid(uid: String)`).
     *
     * @param userId The auto-generated Room ID of the user to retrieve.
     * @return The [User] object if found, or `null` if no user with that ID exists.
     */
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Int): User?

    /**
     * Updates an existing user in the `users` table.
     * This could be used for updating user preferences or status if they were stored locally.
     *
     * --- CURRENT STATUS ---
     * User profile updates (e.g., name, premium status) would primarily occur in Firestore.
     * This method could be used if this Room entity is caching that profile data and needs
     * to reflect updates.
     *
     * @param user The [User] object with updated information. The user's `id` field
     * is used to find the record to update.
     */
    @Update
    suspend fun updateUser(user: User)

    /**
     * Counts the number of users with a specific username.
     * This was useful in a local authentication system to quickly check if a username (email)
     * was already taken before attempting registration.
     *
     * --- CURRENT STATUS ---
     * Firebase Authentication handles username/email collision checks during its
     * user creation process. This method is not used for that purpose anymore.
     *
     * @param username The username (email) to count.
     * @return The number of users found with the given username.
     */
    @Query("SELECT COUNT(*) FROM users WHERE username = :username")
    suspend fun countUsersWithUsername(username: String): Int
}

// General Notes on Annotations and Keywords used (from original file comments):
// - `@Dao`: Marks this as a DAO class for Room.
// - `@Insert`, `@Query`, `@Update`: Room annotations to generate the necessary code for these database operations.
// - `suspend`: We use `suspend` to make these functions callable from Kotlin Coroutines, ensuring database operations don't block the main thread. This is best practice.
// - `onConflict = OnConflictStrategy.IGNORE` (for `@Insert`): If a user with the same primary key
//   already exists, this strategy will ignore the new insert. For registration in a local system,
//   one would typically first check if the username exists using `getUserByUsername` or `countUsersWithUsername`.