package com.mysecunion.app

/**
 * Remote Config keys — see SRS Appendix B.
 * All URLs / copy are externalized here so the site's URL structure
 * (CON-05) can change without an app release.
 */
object RemoteConfigKeys {
    const val BASE_URL = "base_url"
    const val ALLOWED_HOSTS = "allowed_hosts" // JSON string array
    const val TABS = "tabs" // JSON, reserved for FR-202
    const val NOTICE_BANNER = "notice_banner"
    const val MAINTENANCE_MODE = "maintenance_mode"
    const val MAINTENANCE_MESSAGE = "maintenance_message"
    const val LATEST_VERSION = "latest_version"
    const val MIN_SUPPORTED_VERSION = "min_supported_version"
    const val APK_URL = "apk_url"
}
