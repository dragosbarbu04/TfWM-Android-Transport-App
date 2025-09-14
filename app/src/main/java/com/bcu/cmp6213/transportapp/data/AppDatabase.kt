package com.bcu.cmp6213.transportapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Main database class for the application using Room Persistence Library.
 *
 * This abstract class extends RoomDatabase and serves as the main access point
 * to the persisted data.
 *
 * The `@Database` annotation marks this class as a Room database.
 * - `entities`: Lists all the entity classes that are part of this database.
 * Currently, it includes the [User] class. Its role has evolved
 * from primary authentication storage to caching user profile data
 * since Firebase Auth handles authentication.
 * - `version`: Specifies the database version. This is important for migrations if the schema changes.
 * - `exportSchema`: Set to `false` to avoid exporting the schema to a JSON file,
 * which would otherwise generate a build warning if not specified.
 * For production apps with migrations, this should typically be `true`
 * and schemas version controlled.
 */
@Database(entities = [User::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Abstract method to get the Data Access Object (DAO) for the User entity.
     * Room will generate the implementation for this method.
     * @return The UserDao instance.
     */
    abstract fun userDao(): UserDao

    // Companion object allows us to create or get the database instance using a singleton pattern.
    // This ensures that only one instance of the database is ever created.
    companion object {
        // The `@Volatile` annotation ensures that the value of INSTANCE is always up-to-date
        // and the same to all execution threads. It means that writes to this field
        // are immediately made visible to other threads.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Gets the singleton instance of the AppDatabase.
         *
         * This function implements a thread-safe way to get the database instance.
         * If an instance already exists, it's returned. Otherwise, a new database instance
         * is created.
         *
         * @param context The application context.
         * @return The singleton AppDatabase instance.
         */
        fun getDatabase(context: Context): AppDatabase {
            // Return the existing INSTANCE if it's not null.
            // If it is null, enter a synchronized block to ensure that only one thread
            // can create the database instance at a time, preventing race conditions.
            return INSTANCE ?: synchronized(this) {
                // Inside the synchronized block, check again if INSTANCE is still null.
                // This double-check locking pattern ensures that the instance is only created once.
                val instance = Room.databaseBuilder(
                    context.applicationContext, // Use application context to avoid memory leaks.
                    AppDatabase::class.java,    // The AppDatabase class itself.
                    "transport_app_database"    // The name of the database file on disk.
                )
                    // In a production app with evolving database schemas, you would add migration strategies here.
                    // For example: .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // `fallbackToDestructiveMigration()` is used here for simplicity in a prototype.
                    // If the schema version increases and no migration path is provided,
                    // Room will clear all tables and recreate the database. This means existing data will be lost.
                    // Avoid this in production unless data loss is acceptable or handled.
                    .fallbackToDestructiveMigration()
                    .build() // Builds the database instance.

                // Assign the newly created instance to INSTANCE.
                INSTANCE = instance
                // Return the instance.
                instance
            }
        }
    }
}