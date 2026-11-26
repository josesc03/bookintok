package com.bookintok.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BookintokApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initFirebase()
    }

    /**
     * Firebase is configured from BuildConfig (filled from local.properties) instead of a committed
     * google-services.json, so the repository holds no project configuration.
     */
    private fun initFirebase() {
        if (FirebaseApp.getApps(this).isNotEmpty()) return // already initialised in this process
        if (BuildConfig.FIREBASE_PROJECT_ID.isBlank()) {
            Log.w("BookinTok", "Firebase is not configured: set firebase.* in local.properties")
            return
        }
        val options = FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .build()
        FirebaseApp.initializeApp(this, options)
    }
}
