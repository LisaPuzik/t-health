package com.thealth.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealth.app.data.ApiCache
import com.thealth.app.data.ApiClient
import com.thealth.app.data.AuthStore
import com.thealth.app.data.CommunityDto
import com.thealth.app.ui.components.AppCard
import com.thealth.app.ui.components.Avatar
import com.thealth.app.ui.components.EmptyHint
import com.thealth.app.ui.components.SectionTitle
import com.thealth.app.ui.theme.GrayBg
import com.thealth.app.ui.theme.GrayText
import com.thealth.app.ui.theme.Ink
import com.thealth.app.ui.theme.Yellow
import kotlinx.coroutines.launch

@Composable
fun CommunitiesTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<CommunityDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<Int?>(null) }

    fun load() {
        scope.launch {
            try { items = ApiCache.get("communities") { ApiClient.service(AuthStore(ctx)).communities() } }
            catch (_: Exception) { } finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun toggle(c: CommunityDto) {
        scope.launch {
            busy = c.id
            try {
                val api = ApiClient.service(AuthStore(ctx))
                if (c.joined) api.leaveCommunity(c.id) else api.joinCommunity(c.id)
                ApiCache.invalidate("communities")
                load()
            } catch (e: Exception) {
                Toast.makeText(ctx, "${e.message}", Toast.LENGTH_SHORT).show()
            }
            busy = null
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 44.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Сообщества", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(GrayBg).padding(10.dp)
                    .clickable { Toast.makeText(ctx, "Каталог скоро", Toast.LENGTH_SHORT).show() }) {
                    Text("☰", fontSize = 16.sp)
                }
            }
        }

        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ink)
                }
            }
        } else {
            val mine = items.filter { it.joined }
            item { Column(Modifier.padding(horizontal = 20.dp)) { SectionTitle("Мои чаты") } }
            if (mine.isEmpty()) {
                item { EmptyHint("Ты пока никуда не вступил — выбери ниже") }
            } else {
                items(mine) { c ->
                    Row(Modifier.padding(horizontal = 20.dp)) {
                        AppCard(onClick = {
                            Toast.makeText(ctx, "Чаты появятся в следующей версии", Toast.LENGTH_SHORT).show()
                        }) {
                            Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(c.name.firstOrNull()?.toString() ?: "?")
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("${c.members} уч.", color = GrayText, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            item { Column(Modifier.padding(horizontal = 20.dp)) { SectionTitle("Каталог сообществ") } }
            items(items) { c ->
                Row(Modifier.padding(horizontal = 20.dp)) {
                    AppCard {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(c.name.firstOrNull()?.toString() ?: "?")
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("${c.members} участников", color = GrayText, fontSize = 13.sp)
                            }
                            TextButton(onClick = { toggle(c) }, enabled = busy == null) {
                                Text(if (c.joined) "Выйти" else "Вступить",
                                    color = if (c.joined) GrayText else Ink,
                                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
