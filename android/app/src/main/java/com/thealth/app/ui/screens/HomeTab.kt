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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import com.thealth.app.data.AiRecoItem
import com.thealth.app.data.CreateWorkoutRequest
import com.thealth.app.data.CrystalDto
import com.thealth.app.data.EmotionRequest
import com.thealth.app.data.HealthSummary
import com.thealth.app.data.MeResponse
import com.thealth.app.data.RecoItem
import com.thealth.app.data.STEP_GOAL
import com.thealth.app.data.StepUpload
import com.thealth.app.data.UpdateNameRequest
import com.thealth.app.data.UserChallengeDto
import com.thealth.app.data.UserDto
import com.thealth.app.data.friendlyError
import com.thealth.app.data.hasActivityPermission
import com.thealth.app.data.readTodaySteps
import com.thealth.app.data.uploadHourBucket
import com.thealth.app.ui.components.AppCard
import com.thealth.app.ui.components.Avatar
import com.thealth.app.ui.components.Bar
import com.thealth.app.ui.components.EmptyHint
import com.thealth.app.ui.components.SectionTitle
import com.thealth.app.ui.components.StepsRing
import com.thealth.app.ui.components.YellowButton
import com.thealth.app.ui.components.contentEmoji
import com.thealth.app.ui.components.fmt
import com.thealth.app.ui.theme.GrayBg
import com.thealth.app.ui.theme.GrayText
import com.thealth.app.ui.theme.Ink
import com.thealth.app.ui.theme.Yellow
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private data class HomeData(
    val me: MeResponse,
    val todaySteps: Int,
    val summary: HealthSummary,
    val streak: Int,
    val level: Int,
    val week: UserChallengeDto?,
    val recos: List<RecoItem>,
    val aiItems: List<AiRecoItem>,
    val aiSource: String,
    val colleagues: List<UserDto>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(onOpenChallenges: () -> Unit, onOpenCommunities: () -> Unit, onOpenPet: () -> Unit, onOpenNews: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<MeResponse?>(null) }
    var todaySteps by remember { mutableStateOf<Int?>(null) }
    var summary by remember { mutableStateOf<HealthSummary?>(null) }
    var streak by remember { mutableStateOf(0) }
    var crystalLevel by remember { mutableStateOf(1) }
    var weekChallenge by remember { mutableStateOf<UserChallengeDto?>(null) }
    var recos by remember { mutableStateOf<List<RecoItem>>(emptyList()) }
    var aiRecos by remember { mutableStateOf<List<AiRecoItem>>(emptyList()) }
    var aiSource by remember { mutableStateOf("rules") }
    var colleagues by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var showMood by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }
    var goal by remember { mutableStateOf(STEP_GOAL) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Запрос на подсчёт шагов — один раз при входе (штатный диалог, показывается всегда).
    // Дальше: чтение сенсора + тихая отправка часового бакета.
    var sensorApplied by remember { mutableStateOf(false) }
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        try {
            val store = AuthStore(ctx)
            val (_, _, syncOn) = store.privacy()
            if (syncOn && !store.sensorAsked() && !hasActivityPermission(ctx)) {
                store.setSensorAsked()
                try {
                    permLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }
    var newsUnread by remember { mutableStateOf(0) }

    fun load(force: Boolean = false) {
        scope.launch {
            try {
                if (force) ApiCache.invalidate("home")
                val store = AuthStore(ctx)
                val d: HomeData = ApiCache.get("home") {
                    val api = ApiClient.service(store)
                    val m = api.me()
                    val startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant().toString()
                    val uid = store.userId() ?: m.id
                    val ai = api.aiRecommendations()
                    HomeData(
                        me = m,
                        todaySteps = api.summary(from = startOfDay).totalSteps,
                        summary = api.summary(),
                        streak = api.streak().currentStreak,
                        level = api.crystal().level,
                        week = api.userChallenges(uid).firstOrNull { !it.isCompleted },
                        recos = api.recommendations().items.take(3),
                        aiItems = ai.items,
                        aiSource = ai.source,
                        colleagues = api.users().filter { it.id != uid }.take(2)
                    )
                }
                me = d.me
                goal = d.me.dailyStepGoal.coerceIn(1000, 50000)
                if (!sensorApplied) todaySteps = d.todaySteps
                summary = d.summary
                streak = d.streak; crystalLevel = d.level; weekChallenge = d.week
                recos = d.recos; aiRecos = d.aiItems; aiSource = d.aiSource
                colleagues = d.colleagues
                loadError = null
            } catch (e: Exception) {
                loadError = e.message ?: "нет связи"
            } finally { loading = false }
        }
    }

    LaunchedEffect(Unit) {
        load()
        // Бейдж непрочитанных постов ленты (колокольчик)
        scope.launch {
            try {
                newsUnread = ApiCache.get("newsBadge", ttlMs = 60_000) {
                    val store = AuthStore(ctx)
                    val f = ApiClient.service(store).feed(pageSize = 50)
                    val seen = try { store.lastFeedSeenMs() } catch (_: Exception) { null }
                    if (seen == null) f.total.coerceAtMost(50)
                    else f.items.count {
                        runCatching {
                            java.time.Instant.parse(it.createdAt).toEpochMilli() > seen
                        }.getOrDefault(false)
                    }
                }
            } catch (_: Exception) { }
        }
        // Сенсор: правда устройства в кольцо + тихая отправка бакета на бэк
        scope.launch {
            try {
                val store = AuthStore(ctx)
                if (!store.privacy().third) return@launch
                val device = readTodaySteps(ctx, store)
                if (device != null) {
                    todaySteps = device
                    sensorApplied = true
                }
                when (uploadHourBucket(ctx, store, ApiClient.service(store))) {
                    is StepUpload.Uploaded -> ApiCache.invalidate("pet", "profile")
                    else -> {}
                }
            } catch (_: Exception) { }
        }
    }

    val date = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru")))
            .replaceFirstChar { it.uppercase() }
    }

    LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
        item {
            // Header
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 44.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Привет, ${me?.firstName ?: "…"} 👋", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text(date, color = GrayText, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Yellow)
                        .clickable { onOpenPet() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text("💎", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.clickable { onOpenNews() }) {
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(GrayBg).padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔔", fontSize = 16.sp)
                        }
                        if (newsUnread > 0) {
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp)
                                    .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                                    .clip(CircleShape).background(Ink)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (newsUnread > 9) "9+" else "$newsUnread",
                                    color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
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
            if (loadError != null && me == null) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        AppCard {
                            Column(Modifier.padding(16.dp)) {
                                Text("Нет связи с сервером", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(loadError ?: "", color = GrayText, fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp))
                                Spacer(Modifier.height(12.dp))
                                YellowButton("Повторить") { loading = true; load(force = true) }
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    // Today card
                    AppCard {
                        Column(Modifier.padding(20.dp)) {
                            Text("СЕГОДНЯ", color = GrayText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.clickable { showGoal = true }) {
                                StepsRing(todaySteps, goal)
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 18.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("⏱ ${summary?.totalWorkoutMinutes ?: 0} мин за 7 дней", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) {
                                    YellowButton("+ Активность", fontSize = 14.sp) { showAdd = true }
                                }
                                Box(Modifier.weight(1f)) {
                                    YellowButton("+ Настроение", fontSize = 14.sp) { showMood = true }
                                }
                            }
                        }
                    }

                    // Challenge of week
                    weekChallenge?.let { ch ->
                        Spacer(Modifier.height(16.dp))
                        AppCard(onClick = onOpenChallenges) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("🏆 Челлендж недели", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Yellow)
                                            .padding(horizontal = 9.dp, vertical = 4.dp))
                                    Text("›", color = GrayText, fontSize = 17.sp)
                                }
                                Text(ch.title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(top = 10.dp))
                                Spacer(Modifier.height(12.dp))
                                Bar(ch.currentValue.toFloat() / ch.targetValue.coerceAtLeast(1))
                                Text("Прогресс: ${fmt(ch.currentValue)} из ${fmt(ch.targetValue)}",
                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 10.dp))
                                Text("Осталось ${daysLeft(ch.endDate)}", color = GrayText, fontSize = 13.sp)
                            }
                        }
                    }

                    SectionTitle("✨ AI-подборка")
                }
            }

            items(aiRecos) { r ->
                Row(Modifier.padding(horizontal = 20.dp)) {
                    AppCard(onClick = { Toast.makeText(ctx, r.title, Toast.LENGTH_SHORT).show() }) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(contentEmoji(r.contentType), fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(r.description, color = GrayText, fontSize = 13.sp, maxLines = 2)
                                Text(
                                    (if (aiSource == "ai") "✨ ИИ · " else "") + r.reason,
                                    color = GrayText, fontSize = 12.sp, maxLines = 2,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Text("→", color = GrayText, fontSize = 16.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SectionTitle("🎯 Сегодня для тебя")
                }
            }

            items(recos) { r ->
                Row(Modifier.padding(horizontal = 20.dp)) {
                    AppCard(onClick = { Toast.makeText(ctx, r.title, Toast.LENGTH_SHORT).show() }) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(contentEmoji(r.contentType), fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(r.description, color = GrayText, fontSize = 13.sp, maxLines = 2)
                            }
                            Text("→", color = GrayText, fontSize = 16.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SectionTitle("👥 Что делают коллеги")
                }
            }

            if (colleagues.isEmpty()) {
                item { EmptyHint("Пока тихо — коллеги ещё не активничали") }
            } else {
                items(colleagues) { u ->
                    Row(Modifier.padding(horizontal = 20.dp)) {
                        AppCard {
                            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(u.firstName.firstOrNull()?.toString() ?: "?")
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("${u.firstName}, ${u.team ?: "без команды"}",
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("${fmt(u.totalPoints)} очков", color = GrayText, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            item {
                Text("Смотреть все →", color = GrayText, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        .clickable { onOpenCommunities() }.padding(vertical = 4.dp))
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAdd) AddActivitySheet(
        onClose = { showAdd = false },
        onAdded = { ApiCache.invalidate("home", "pet", "profile"); load(force = true) }
    )

    if (showMood) MoodSheet(
        onClose = { showMood = false },
        onSaved = { ApiCache.invalidate("home"); load(force = true) }
    )

    if (showGoal) GoalSheet(
        current = goal,
        onClose = { showGoal = false },
        onSaved = {
            ApiCache.invalidate("home", "profile")
            load(force = true)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalSheet(current: Int, onClose: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var value by remember(current) { mutableStateOf(current.toString()) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(20.dp)) {
            Text("Цель на день", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Сколько шагов хочешь проходить ежедневно",
                color = GrayText, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = value, onValueChange = { value = it.filter(Char::isDigit).take(5) },
                label = { Text("Шагов в день") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (err != null) Text(err!!, color = Color.Red, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(16.dp))
            YellowButton("Сохранить", loading = busy) {
                val goal = value.toIntOrNull()
                if (goal == null || goal < 1000 || goal > 50000) {
                    err = "Цель — от 1000 до 50000 шагов"
                    return@YellowButton
                }
                scope.launch {
                    busy = true; err = null
                    try {
                        ApiClient.service(AuthStore(ctx)).updateMe(UpdateNameRequest(dailyStepGoal = goal))
                        onClose(); onSaved()
                    } catch (e: Exception) { err = friendlyError(e) }
                    busy = false
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

fun daysLeft(endIso: String): String {
    return try {
        val end = java.time.Instant.parse(endIso).atZone(ZoneOffset.UTC).toLocalDate()
        val d = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), end).toInt()
        when {
            d < 0 -> "завершён"
            d == 0 -> "последний день"
            d == 1 -> "Остался 1 день"
            d in 2..4 -> "Осталось $d дня"
            else -> "Осталось $d дней"
        }
    } catch (_: Exception) { "" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddActivitySheet(onClose: () -> Unit, onAdded: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("Running") }
    var minutes by remember { mutableStateOf("30") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val types = listOf("Бег", "Ходьба", "Йога", "Велосипед", "Силовая", "Плавание", "Тренировка")

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(20.dp)) {
            Text("Новая активность", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                types.forEach { t ->
                    val sel = t == type
                    Box(Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Yellow else GrayBg)
                        .clickable { type = t }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(t, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                label = { Text("Минуты") }, modifier = Modifier.fillMaxWidth())
            if (err != null) Text(err!!, color = Color.Red, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            YellowButton("Сохранить", loading = busy) {
                val min = minutes.toIntOrNull()
                if (min == null || min <= 0) { err = "Укажи минуты"; return@YellowButton }
                scope.launch {
                    busy = true; err = null
                    try {
                        val store = AuthStore(ctx)
                        val uid = store.userId() ?: return@launch
                        val res = ApiClient.service(store)
                            .createWorkout(CreateWorkoutRequest(uid, type, min))
                        Toast.makeText(ctx, "+${res.workout.points} очков", Toast.LENGTH_SHORT).show()
                        onClose(); onAdded()
                    } catch (e: Exception) { err = friendlyError(e) }
                    busy = false
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoodSheet(onClose: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mood by remember { mutableStateOf(4) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val faces = listOf("😞", "😕", "😐", "🙂", "😄")

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(20.dp)) {
            Text("Как ты себя чувствуешь?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                faces.forEachIndexed { i, emoji ->
                    val sel = mood == i + 1
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .background(if (sel) Yellow else GrayBg)
                            .clickable { mood = i + 1 }
                            .padding(10.dp)
                    ) { Text(emoji, fontSize = 28.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = note, onValueChange = { if (it.length <= 500) note = it },
                label = { Text("Заметка (необязательно)") }, modifier = Modifier.fillMaxWidth())
            if (err != null) Text(err!!, color = Color.Red, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            YellowButton("Сохранить", loading = busy) {
                scope.launch {
                    busy = true; err = null
                    try {
                        val store = AuthStore(ctx)
                        val uid = store.userId() ?: return@launch
                        ApiClient.service(store).createEmotion(
                            EmotionRequest(uid, mood, note.ifBlank { null })
                        )
                        Toast.makeText(ctx, "Настроение записано 🌤️", Toast.LENGTH_SHORT).show()
                        onClose(); onSaved()
                    } catch (e: Exception) { err = friendlyError(e) }
                    busy = false
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
