package com.mysecunion.app

import android.app.Application
import com.google.firebase.FirebaseApp

class MySecUnionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        // FR-302 topic subscription lives in MainActivity (needs an Activity to guarantee/retry
        // via an activity-scoped listener) — see MainActivity.ensureNoticeTopicSubscription().
    }
}
