package com.gsc.stockoverview.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.gsc.stockoverview.ui.components.StockTopAppBar

@Composable
fun PortfolioScreen(onOpenDrawer: () -> Unit) {
    Scaffold(
        topBar = {
            StockTopAppBar(title = "포트폴리오", onOpenDrawer = onOpenDrawer)
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("포트폴리오 화면 준비 중입니다.")
        }
    }
}
