package com.project2.coffeeshoppos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.project2.coffeeshoppos.ui.CoffeeShopApp
import com.project2.coffeeshoppos.ui.theme.CoffeeShopPOSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoffeeShopPOSTheme {
                CoffeeShopApp()
            }
        }
    }

    // Leaving the app for a while signs out, so an unattended phone is not left open.
    override fun onStart() {
        super.onStart()
        (application as CoffeeShopApplication).container.sessionManager.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        (application as CoffeeShopApplication).container.sessionManager.onAppBackgrounded()
    }
}
