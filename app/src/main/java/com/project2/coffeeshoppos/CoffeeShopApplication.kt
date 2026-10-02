package com.project2.coffeeshoppos

import android.app.Application

/** Runs once when the app process starts, before any screen. Holds the shared [AppContainer]. */
class CoffeeShopApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
