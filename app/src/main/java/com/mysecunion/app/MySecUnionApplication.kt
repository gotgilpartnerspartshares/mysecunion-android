package com.mysecunion.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

class MySecUnionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // FR-302: auto-subscribe every install to the notice topic
        FirebaseMessaging.getInstance().subscribeToTopic(getString(R.string.fcm_topic_notice))
    }
}
