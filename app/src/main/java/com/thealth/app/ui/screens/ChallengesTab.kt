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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
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
import com.thealth.app.data.ChallengeDto
import com.thealth.app.data.JoinRequest
import com.thealth.app.data.TeamEntry
import com.thealth.app.data.UserChallengeDto
import com.thealth.app.ui.components.AppCard
import com.thealth.app.ui.components.Bar
import com.thealth.app.ui.components.EmptyHint
import com.thealth.app.ui.components.SectionTitle
import com.thealth.app.ui.components.fmt
import com.thealth.app.ui.components.metricEmoji
import com.thealth.app.ui.theme.GrayBg
import com.thealth.app.ui.theme.GrayText
import com.thealth.app.ui.theme.Ink
import com.thealth.app.ui.theme.Yellow
import kotlinx.coroutines.launch

private data class ChallengesData(
    val all: List<ChallengeDto>,
    val mine: List<UserChallengeDto>,
    val board: List<TeamEntry>,
    val team: String?
)

@Composable
fun ChallengesTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<ChallengeDto>>(emptyList()) }
    var mine by remember { mutableStateOf<List<UserChallengeDto>>(emptyList()) }
    var board by remember { mutableStateOf<List<TeamEntry>>(emptyList()) }
    var myTeam by remember { mutableStateOf<String?>(null) }
    var seg by remember { mutableStateOf(0) } // 0 активные, 1 доступные, 2 завершённые
    var loading by remember { mutableStateOf(true) }
    var joining by remember { mutableStateOf<Int?>(null) }

    fun load() {
        scope.launch {
            try {
                val store = AuthStore(ctx)
                val d: ChallengesData = ApiCache.get("challenges") {
                    val api = ApiClient.service(store)
                    val a = api.challenges()
                    val uid = store.userId() ?: api.me().id
                    ChallengesData(
                        all = a,
                        mine = api.userChallenges(uid),
                        board = api.leaderboard(),
                        team = api.me().team
                    )
                }
                all = d.all; mine = d.mine; board = d.board; myTeam = d.team
            } catch (_: Exception) {
            } finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { load() }

    val joinedIds = mine.map { it.challengeId }.toSet()
    val doneIds = mine.filter { it.isCompleted }.map { it.challengeId }.toSet()
    val shown = when (seg) {
        0 -> all.filter { joinedIds.contains(it.id) && !doneIds.contains(it.id) }
        1 -> all.filter { !joinedIds.contains(it.id) }
        else -> all.filter { doneIds.contains(it.id) }
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                // Header
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 44.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Челленджи", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(GrayBg).padding(10.dp)
                        .clickable { Toast.makeText(ctx, "Фильтры скоро", Toast.LENGTH_SHORT).show() }) {
                        Text("☰", fontSize = 16.sp)
                    }
                }
                // Segment
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(12.dp)).background(GrayBg).padding(4.dp)) {
                    listOf("Активные", "Доступные", "Завершённые").forEachIndexed { i, label ->
                        Box(Modifier.weight(1f).height(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (seg == i) Yellow else Color.Transparent)
                            .clickable { seg = i },
                            contentAlignment = Alignment.Center) {
                            Text(label, fontSize = 13.sp,
                                fontWeight = if (seg == i) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (seg == i) Ink else GrayText)
                        }
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
                // Rating
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        AppCard {
                            Column(Modifier.padding(16.dp)) {
                                Text("🏆 Рейтинг отделов", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                val max = board.firstOrNull()?.totalPoints?.coerceAtLeast(1) ?: 1
                                board.take(3).forEachIndexed { i, t ->
                                    Row(Modifier.padding(top = 8.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("${i + 1}", color = GrayText, fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
                                        Text(t.name + if (t.name == myTeam) " · твой" else "",
                                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.width(88.dp))
                                        Bar(t.totalPoints.toFloat() / max, Modifier.weight(1f))
                                        Text(fmt(t.totalPoints), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(52.dp))
                                    }
                                }
                            }
                        }
                        SectionTitle(if (seg == 0) "Активные челленджи" else if (seg == 1) "Доступные челленджи" else "Завершённые")
                    }
                }

                if (shown.isEmpty()) {
                    item { EmptyHint("Здесь пока пусто") }
                } else {
                    items(shown) { ch ->
                        Row(Modifier.padding(horizontal = 20.dp)) {
                            val prog = mine.firstOrNull { it.challengeId == ch.id }
                            AppCard(onClick = {
                                if (!ch.joined) {
                                    scope.launch {
                                        joining = ch.id
                                        try {
                                            val store = AuthStore(ctx)
                                            val uid = store.userId() ?: return@launch
                                            ApiClient.service(store).joinChallenge(ch.id, JoinRequest(uid))
                                            Toast.makeText(ctx, "Ты в челлендже!", Toast.LENGTH_SHORT).show()
                                            ApiCache.invalidate("challenges", "home")
                                            load()
                                        } catch (e: Exception) {
                                            Toast.makeText(ctx, "${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                        joining = null
                                    }
                                }
                            }) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Личный", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(GrayBg)
                                                .padding(horizontal = 9.dp, vertical = 4.dp))
                                        Text("›", color = GrayText, fontSize = 17.sp)
                                    }
                                    Text("${metricEmoji(ch.metricType)} ${ch.title}",
                                        fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(top = 10.dp))
                                    Spacer(Modifier.height(12.dp))
                                    val cur = prog?.currentValue ?: 0
                                    Bar(cur.toFloat() / ch.targetValue.coerceAtLeast(1))
                                    Text(
                                        if (prog != null) "${fmt(cur)} из ${fmt(ch.targetValue)}"
                                        else "Нажми, чтобы участвовать · +${ch.points} очков",
                                        color = GrayText, fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 10.dp)
                                    )
                                    Text("${daysLeft(ch.endDate)} · ${ch.participants} уч.",
                                        color = GrayText, fontSize = 13.sp)
                                    if (joining == ch.id)
                                        Text("Вступаем…", color = GrayText, fontSize = 13.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }

        FloatingActionButton(
            onClick = { Toast.makeText(ctx, "Создание челленджей скоро", Toast.LENGTH_SHORT).show() },
            containerColor = Yellow, contentColor = Ink,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 100.dp).size(56.dp)
        ) { Text("+", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
    }
}
