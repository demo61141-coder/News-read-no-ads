package com.example.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

object FirebaseInitStatus {
    @Volatile
    var isFirebaseReady: Boolean = false
        private set

    @Volatile
    var diagnosticMessage: String? = null
        private set

    fun ensureInitialized(context: Context): Boolean {
        val appContext = context.applicationContext
        if (FirebaseApp.getApps(appContext).isNotEmpty()) {
            isFirebaseReady = true
            diagnosticMessage = null
            return true
        }

        val hasResourceConfig = FirebaseOptions.fromResource(appContext) != null
        if (!hasResourceConfig) {
            isFirebaseReady = false
            diagnosticMessage = "Firebase config (google-services.json) is missing in this APK build. " +
                "If you built this APK via GitHub Actions, ensure app/google-services.json is included in the repository or workflow before building, or generate the APK directly from AI Studio."
            Log.e(TAG, diagnosticMessage!!)
            return false
        }

        val initializedApp = FirebaseApp.initializeApp(appContext)
        isFirebaseReady = initializedApp != null || FirebaseApp.getApps(appContext).isNotEmpty()
        if (!isFirebaseReady) {
            diagnosticMessage = "FirebaseApp failed to initialize from app resources."
            Log.e(TAG, diagnosticMessage!!)
        } else {
            diagnosticMessage = null
        }
        return isFirebaseReady
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        val appContext = context.applicationContext
        if (!FirebaseInitStatus.ensureInitialized(appContext)) {
            return
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = appContext.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronous commit() ensures the debug secret is persisted before provider init.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = appContext.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                appContext.packageName
            )
            if (resId != 0) {
                val siteKey = appContext.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
