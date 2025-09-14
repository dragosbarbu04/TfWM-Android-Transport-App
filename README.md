# Transport for West Midlands (TfWM) - Android Transit App

> **Note:** This project was developed as the final coursework for the CMP6213 Mobile and Wearable Application Development module at Birmingham City University. It is a functional prototype designed to demonstrate a comprehensive understanding of modern Android development practices. Any API Keys and/or other dependencies are free and need be replaced if you are using this project, the existing keys in the project have been deleted.

This is a native Android application, written in **Kotlin**, that serves as a journey planner for the Transport for West Midlands (TfWM) public transport network. The app provides users with secure authentication, GTFS-based route browsing, interactive map-based journey suggestions, and a mock ticketing system using Firebase services.


---

## Key Features 📋

* **Secure User Authentication:**
    * Full user registration and login system using **Firebase Authentication** (email/password).
    * Secure session management to keep users logged in across app launches.
    * User profile data (name, email) stored in **Cloud Firestore**.

* **GTFS Data Integration:**
    * Downloads and caches the official TfWM GTFS data feed via the TfWM API.
    * Parses GTFS files (`routes.txt`, `stops.txt`, etc.) to provide comprehensive route and stop information.
    * Features an efficient on-demand parsing system for `shapes.txt` to handle large datasets and prevent memory errors.

* **Interactive Journey Planning Map:**
    * Displays the user's current location using the **Google Maps SDK**.
    * Allows users to search for a destination, which is converted to coordinates using the Android Geocoder.
    * **Suggests direct routes** by finding nearby stops and calculating the best trip based on service availability and departure times.
    * **Draws the route path** on the map using a polyline by extracting the correct segment from the GTFS shape data.

* **Mock Ticketing System:**
    * Users can "purchase" mock tickets for suggested journeys.
    * Ticket data is saved to the user's account in a dedicated **Cloud Firestore** collection, linked by their Firebase UID.
    * A "My Tickets" tab displays a list of all purchased tickets with detailed journey information.

* **User Profile & Settings:**
    * A profile screen displays the logged-in user's name and email.
    * Features a theme switcher to toggle between **Light and Dark Mode**, with the preference saved locally using SharedPreferences.

---

## Project Architecture & Design Patterns 🏗️

This application was built with a modern, robust architecture to ensure separation of concerns, testability, and a responsive user interface.

* **MVVM (Model-View-ViewModel):** The core architectural pattern.
    * **Views (Activities/Fragments):** Responsible for the UI and forwarding user actions. Uses a single-activity architecture with multiple fragments.
    * **ViewModels (`GtfsViewModel`, `TicketsViewModel`):** Manage UI-related data, handle business logic, and survive configuration changes.
    * **Model (Data Layer):** Consists of data classes (e.g., `Ticket`, GTFS models), network services (Retrofit), Firebase services, and local caching logic.
* **Asynchronous Operations:** **Kotlin Coroutines** are used for all background tasks (networking, file I/O, GTFS parsing) to keep the UI from freezing.
* **Reactive UI:** **LiveData** is used to expose data from ViewModels to the UI, allowing the UI to react automatically to data changes in a lifecycle-aware manner.
* **Key Design Patterns:**
    * **Repository Pattern (Implicit):** `GtfsViewModel` acts as the single source of truth for all GTFS data.
    * **Adapter Pattern:** Used extensively in `RecyclerViews` to display lists of routes, stops, and tickets.
    * **Singleton Pattern:** A `SessionManager` object provides a single instance for managing user login state.

---

## Technologies, Libraries & APIs 🛠️

* **Language:** Kotlin
* **IDE:** Android Studio
* **Core Libraries:**
    * **Firebase:** Authentication, Cloud Firestore 
    * **Google Maps SDK:** For all map functionalities
    * **Google Play Services:** For location services (`FusedLocationProviderClient`) 
    * **Retrofit & OkHttp:** For efficient and robust networking (downloading the GTFS feed) [cite: 415]
    * **Android Architecture Components:** ViewModel, LiveData, ViewBinding 
    * **Kotlin Coroutines:** For managing background threads 

---

## Challenges & Solutions 🧠

This project involved solving several real-world development challenges:

1.  **Challenge: `OutOfMemoryError`**
    * **Problem:** The GTFS dataset, particularly `shapes.txt`, was too large to load entirely into memory.
    * **Solution:** An **on-demand parsing** system was implemented. Instead of loading the entire file, the app reads only the specific lines needed for a given route's shape when it's requested, drastically reducing memory usage.

2.  **Challenge: Network Security**
    * **Problem:** The TfWM API uses HTTP, which is blocked by default on modern Android versions.
    * **Solution:** A `network_security_config.xml` file was created to explicitly allow HTTP cleartext traffic **only** for the `api.tfwm.org.uk` domain, maintaining security for all other connections.

---

## How to Set Up & Run

1.  **Clone the repository.**
2.  **API Keys:** You will need to provide your own API keys.
    * **Google Maps:** Obtain an API key from the Google Cloud Console and enable the "Maps SDK for Android".
    * **TfWM API:** Register for an API key from the Transport for West Midlands developer portal.
    * Store these keys in a `gradle.properties` file in the root of the project. This project is configured to read them from there to keep them out of version control[cite: 407, 408].
3.  **Firebase:**
    * Create a new project on the [Firebase Console](https://console.firebase.google.com/).
    * Add an Android app to the project with the correct package name.
    * Download the `google-services.json` file and place it in the `app/` directory.
    * Enable **Authentication** (Email/Password method) and **Cloud Firestore**.
4.  **Open in Android Studio**, let Gradle sync, and run the app on an emulator or a physical device.