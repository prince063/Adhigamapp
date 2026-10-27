package com.example.adhigam

import android.app.Application
import com.example.adhigam.data.AppContainer

class AdhigamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
