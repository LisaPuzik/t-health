package com.thealth.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thealth.app.ui.theme.GrayBg
import com.thealth.app.ui.theme.GrayText
import com.thealth.app.ui.theme.Ink

private data class Tab(val route: String, val emoji: String, val label: String)

private val Tabs = listOf(
    Tab("tab_home", "🏠", "Главная"),
    Tab("tab_challenges", "🏆", "Челленджи"),
    Tab("tab_communities", "💬", "Сообщества"),
    Tab("tab_profile", "👤", "Профиль")
)

@Composable
fun MainScaffold(onLogout: () -> Unit) {
    val tabs = rememberNavController()
    val entry by tabs.currentBackStackEntryAsState()
    val showBar = entry?.destination?.route in listOf("tab_home", "tab_challenges", "tab_communities", "tab_profile")
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.weight(1f)) {
            NavHost(tabs, startDestination = "tab_home") {
                composable("tab_home") {
                    HomeTab(
                        onOpenChallenges = { goTab(tabs, "tab_challenges") },
                        onOpenCommunities = { goTab(tabs, "tab_communities") },
                        onOpenPet = { tabs.navigate("pet") },
                        onOpenNews = { tabs.navigate("news") }
                    )
                }
                composable("tab_challenges") { ChallengesTab() }
                composable("tab_communities") { CommunitiesTab() }
                composable("tab_profile") { ProfileTab(onLogout = onLogout) }
                composable("pet") { PetScreen(onBack = { tabs.popBackStack() }) }
                composable("news") { FeedScreen(onBack = { tabs.popBackStack() }) }
            }
        }
        if (showBar) BottomBar(tabs)
    }
}

private fun goTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    Row(Modifier.fillMaxWidth().background(Color.White).padding(top = 8.dp, bottom = 20.dp)) {
        Tabs.forEach { t ->
            val active = current == t.route
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .clickable { goTab(nav, t.route) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(t.emoji, fontSize = 20.sp)
                Text(t.label, fontSize = 11.sp,
                    color = if (active) Ink else GrayText,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                    textAlign = TextAlign.Center)
            }
        }
    }
}
