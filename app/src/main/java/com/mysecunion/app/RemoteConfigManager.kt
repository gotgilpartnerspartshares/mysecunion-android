package com.mysecunion.app

import android.app.Activity
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Wraps FirebaseRemoteConfig for SRS 4.4 (Remote Config) / 4.5 (version gating / Appendix B).
 *
 * Defaults live in res/xml/remote_config_defaults.xml, not code, so they can be read/diffed
 * without opening Kotlin and stay in sync with what ops sets in the Firebase console.
 */
class RemoteConfigManager {

    val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

    init {
        // Debug builds want every fetch to actually hit the network; release respects the
        // console's throttling expectations (and the Spark-plan free-tier fetch quota).
        val minFetchIntervalSeconds = if (BuildConfig.DEBUG) 0L else 3600L
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings { minimumFetchIntervalInSeconds = minFetchIntervalSeconds }
        )
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
    }

    /**
     * FR-401: fetch + activate. Activity-scoped listener — Firebase detaches it automatically
     * at onStop, so a slow/late response can't call back into a destroyed/backgrounded Activity.
     * On failure Remote Config simply keeps the last-activated (or built-in default) values,
     * so callers don't need a separate failure path.
     */
    fun fetchAndActivate(activity: Activity, onComplete: (success: Boolean) -> Unit) {
        remoteConfig.fetchAndActivate().addOnCompleteListener(activity) { task ->
            onComplete(task.isSuccessful)
        }
    }

    fun baseUrl(): String = remoteConfig.getString(RemoteConfigKeys.BASE_URL)

    /** NFR-306: JSON array of allowed navigation hosts; falls back to the SRS default pair. */
    fun allowedHosts(): Set<String> = try {
        val arr = JSONArray(remoteConfig.getString(RemoteConfigKeys.ALLOWED_HOSTS))
        (0 until arr.length()).map { arr.getString(it).lowercase() }.toSet()
    } catch (e: Exception) {
        setOf("secunion.co.kr", "www.secunion.co.kr")
    }

    /**
     * FR-202/203: `tabs` is a flat JSON object of tabId -> URL (see res/xml/remote_config_defaults.xml).
     * Returns null on a missing/unparseable key so callers fall back to their own hardcoded default
     * instead of crashing or loading a blank page (CON-05: the site's URLs can change without notice).
     */
    fun tabUrl(tabId: String): String? = try {
        JSONObject(remoteConfig.getString(RemoteConfigKeys.TABS)).optString(tabId).ifBlank { null }
    } catch (e: Exception) {
        null
    }

    fun isMaintenanceMode(): Boolean = remoteConfig.getBoolean(RemoteConfigKeys.MAINTENANCE_MODE)

    fun maintenanceMessage(): String = remoteConfig.getString(RemoteConfigKeys.MAINTENANCE_MESSAGE)

    fun latestVersion(): String = remoteConfig.getString(RemoteConfigKeys.LATEST_VERSION)

    fun minSupportedVersion(): String = remoteConfig.getString(RemoteConfigKeys.MIN_SUPPORTED_VERSION)

    fun apkUrl(): String = remoteConfig.getString(RemoteConfigKeys.APK_URL)
}
