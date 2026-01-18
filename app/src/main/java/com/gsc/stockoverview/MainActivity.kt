package com.gsc.stockoverview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gsc.stockoverview.ui.MainScreen
import com.gsc.stockoverview.ui.theme.StockOverviewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StockOverviewTheme {
                MainScreen()
            }
        }
    }
}
