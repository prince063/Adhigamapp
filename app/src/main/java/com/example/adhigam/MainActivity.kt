package com.example.adhigam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.adhigam.navigation.AppNavHost
import com.example.adhigam.ui.theme.AdhigamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdhigamTheme {
                AppNavHost()
            }
        }
    }
}
