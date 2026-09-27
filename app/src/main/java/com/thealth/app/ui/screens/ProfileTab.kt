package com.thealth.app.ui.screens

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealth.app.data.AchievementItem
import com.thealth.app.data.ApiCache
import com.thealth.app.data.friendlyError
import com.thealth.app.data.ApiClient
import com.thealth.app.data.AuthStore
import com.thealth.app.data.MeResponse
import com.thealth.app.ui.components.AppCard
import com.thealth.app.ui.components.Avatar
import com.thealth.app.ui.components.PCard
import com.thealth.app.ui.components.SectionTitle
import com.thealth.app.ui.components.YellowButton
import com.thealth.app.ui.components.fmt
import com.thealth.app.ui.theme.GrayBg
import com.thealth.app.ui.theme.GrayText
import com.thealth.app.ui.theme.Ink
import com.thealth.app.ui.theme.Yellow
import kotlinx.coroutines.launch

private data class ProfileData(
    val me: MeResponse,
    val streak: Int,
    val badges: List<AchievementItem>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTab(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<MeResponse?>(null) }
    var streak by remember { mutableStateOf(0) }
    var badges by remember { mutableStateOf<List<AchievementItem>>(emptyList()) }
    var activity by remember { mutableStateOf(true) }
    var mood by remember { mutableStateOf(false) }
    var showBadges by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showNameEdit by remember { mutableStateOf(false) }
    var showInterests by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            try {
                val store = AuthStore(ctx)
                val d: ProfileData = ApiCache.get("profile") {
                    val api = ApiClient.service(store)
                    ProfileData(
                        me = api.me(),
                        streak = api.streak().currentStreak,
                        badges = api.myAchievements().items
                    )
                }
                me = d.me; streak = d.streak; badges = d.badges
                val (a, m, _) = store.privacy()
                activity = a; mood = m
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(Unit) { load() }

    fun savePriv() {
        scope.launch {
            val store = AuthStore(ctx)
            val (_, _, s) = store.privacy()
            store.savePrivacy(activity, mood, s)
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
        item {
            Text("Профиль", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 44.dp, bottom = 8.dp))
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                AppCard {
                    Column(Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(me?.firstName?.firstOrNull()?.toString() ?: "…", big = true)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 12.dp)) {
                            Text("${me?.firstName ?: "…"} ${me?.lastName ?: ""}",
                                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.width(6.dp))
                            Text("✏️", fontSize = 15.sp,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(GrayBg)
                                    .clickable { showNameEdit = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        Text(me?.team ?: "Без команды", color = GrayText, fontSize = 13.sp)
                        Row(Modifier.padding(top = 16.dp)) {
                            Stat(fmt(me?.totalPoints ?: 0), "баллов")
                            Spacer(Modifier.width(20.dp))
                            Stat("🔥 $streak", "дней")
                            Spacer(Modifier.width(20.dp))
                            Stat("${badges.size}", "бейджей")
                        }
                    }
                }
                SectionTitle("Настройки приватности")
                PCard("🏃", "Моя активность для коллег", "Коллеги видят твои шаги и тренировки в ленте.",
                    activity) { activity = it; savePriv() }
                PCard("🧠", "Моё настроение для HR", "Только агрегированные тренды по отделу.",
                    mood) { mood = it; savePriv() }
                SectionTitle("Ещё")
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                MenuRow("🏅", "Мои достижения", "${badges.size} бейджей") { showBadges = true }
                Spacer(Modifier.height(10.dp))
                MenuRow("📜", "История", "Тренировки и настроение") { showHistory = true }
                Spacer(Modifier.height(10.dp))
                MenuRow("🎯", "Мои интересы", "Что тебе интересно") { showInterests = true }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = {
                    scope.launch { AuthStore(ctx).clear(); ApiCache.invalidateAll(); onLogout() }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Выйти", color = GrayText, fontSize = 15.sp)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showBadges) ModalBottomSheet(onDismissRequest = { showBadges = false }) {
        Column(Modifier.padding(20.dp)) {
            Text("Мои достижения", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (badges.isEmpty()) {
                Text("Пока пусто — закрой первый челлендж 💪", color = GrayText, fontSize = 14.sp)
            } else {
                badges.forEach { b ->
                    Row(Modifier.padding(vertical = 8.dp)) {
                        Text("🏅", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(b.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(b.description, color = GrayText, fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showHistory) HistorySheet(onClose = { showHistory = false })

    if (showHistory) HistorySheet(onClose = { showHistory = false })

    if (showNameEdit) NameSheet(
        first = me?.firstName.orEmpty(),
        last = me?.lastName.orEmpty(),
        onClose = { showNameEdit = false },
        onSaved = { f, l ->
            me = me?.copy(firstName = f, lastName = l)
            ApiCache.invalidate("profile")
        }
    )

    if (showInterests) InterestsSheet(onClose = { showInterests = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameSheet(first: String, last: String, onClose: () -> Unit, onSaved: (String, String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var f by remember(first) { mutableStateOf(first) }
    var l by remember(last) { mutableStateOf(last) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(20.dp)) {
            Text("Имя и фамилия", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = f, onValueChange = { if (it.length <= 100) f = it },
                label = { Text("Имя") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = l, onValueChange = { if (it.length <= 100) l = it },
                label = { Text("Фамилия") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (err != null) Text(err!!, color = Color.Red, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(16.dp))
            YellowButton("Сохранить", loading = busy) {
                if (f.isBlank()) { err = "Введи имя"; return@YellowButton }
                if (l.isBlank()) { err = "Введи фамилию"; return@YellowButton }
                scope.launch {
                    busy = true; err = null
                    try {
                        val r = ApiClient.service(AuthStore(ctx))
                            .updateMe(com.thealth.app.data.UpdateNameRequest(f.trim(), l.trim()))
                        onSaved(r.firstName, r.lastName)
                        onClose()
                    } catch (e: Exception) { err = com.thealth.app.data.friendlyError(e) }
                    busy = false
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InterestsSheet(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<com.thealth.app.data.InterestDto>>(emptyList()) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val list = ApiClient.service(AuthStore(ctx)).interests()
                items = list
                picked = list.filter { it.selected }.map { it.id }.toSet()
            } catch (_: Exception) { } finally { loading = false }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Мои интересы", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ink)
                }
            } else {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEach { it ->
                        val (emoji, label) = com.thealth.app.ui.components.interestUi(it.name)
                        val sel = picked.contains(it.id)
                        Box(Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Yellow else GrayBg)
                            .clickable { picked = if (sel) picked - it.id else picked + it.id }
                            .padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("$emoji $label", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                YellowButton("Сохранить", loading = busy) {
                    scope.launch {
                        busy = true
                        try {
                            ApiClient.service(AuthStore(ctx))
                                .setInterests(com.thealth.app.data.SetInterestsRequest(picked.toList()))
                            ApiCache.invalidate("home", "profile")
                            onClose()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(ctx,
                                com.thealth.app.data.friendlyError(e),
                                android.widget.Toast.LENGTH_SHORT).show()
                        }
                        busy = false
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var workouts by remember { mutableStateOf<List<com.thealth.app.data.WorkoutHistoryItem>>(emptyList()) }
    var moods by remember { mutableStateOf<List<com.thealth.app.data.MoodHistoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var month by remember { mutableStateOf(java.time.YearMonth.now()) }
    var selected by remember { mutableStateOf<java.time.LocalDate?>(null) }

    fun dayOf(iso: String): java.time.LocalDate? = try {
        java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    } catch (_: Exception) { null }

    fun timeOf(iso: String): String = try {
        java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) { "" }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val store = AuthStore(ctx)
                val api = ApiClient.service(store)
                val uid = store.userId() ?: return@launch
                workouts = api.workouts(uid)
                moods = api.emotions(uid)
            } catch (_: Exception) { } finally { loading = false }
        }
    }

    val workoutDays = remember(workouts) {
        workouts.mapNotNull { dayOf(it.createdAt) to it }.groupBy({ it.first }, { it.second })
    }
    val moodDays = remember(moods) {
        moods.mapNotNull { dayOf(it.createdAt) to it }.groupBy({ it.first }, { it.second })
    }
    val faces = listOf("", "😞", "😕", "😐", "🙂", "😄")
    val today = remember { java.time.LocalDate.now() }
    val monthTitle = remember(month) {
        month.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", java.util.Locale("ru")))
            .replaceFirstChar { it.uppercase() }
    }
    // Сетка месяца: понедельник первым
    val cells = remember(month) {
        val first = month.atDay(1)
        val lead = (first.dayOfWeek.value - 1) % 7
        val list = mutableListOf<java.time.LocalDate?>()
        repeat(lead) { list += null }
        for (d in 1..month.lengthOfMonth()) list += month.atDay(d)
        while (list.size % 7 != 0) list += null
        list
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("История", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(GrayBg)
                        .clickable { month = month.minusMonths(1) }.padding(horizontal = 12.dp, vertical = 4.dp))
                Text(monthTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center)
                Text("›", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(GrayBg)
                        .clickable { if (month < java.time.YearMonth.now()) month = month.plusMonths(1) }
                        .padding(horizontal = 12.dp, vertical = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                    Text(it, color = GrayText, fontSize = 11.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ink)
                }
            } else {
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            if (day == null || day.isAfter(today)) {
                                Box(Modifier.weight(1f).height(46.dp))
                            } else {
                                val hasW = workoutDays.containsKey(day)
                                val dayMoods = moodDays[day].orEmpty()
                                val sel = day == selected
                                Column(
                                    Modifier.weight(1f).height(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                sel -> Yellow
                                                day == today -> GrayBg
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable(enabled = hasW || dayMoods.isNotEmpty()) {
                                            selected = if (sel) null else day
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text("${day.dayOfMonth}", fontSize = 14.sp,
                                        fontWeight = if (day == today || sel) FontWeight.Bold else FontWeight.Normal)
                                    Row(horizontalArrangement = Arrangement.Center) {
                                        if (hasW) Text("💪", fontSize = 9.sp)
                                        dayMoods.firstOrNull()?.let {
                                            Text(faces.getOrElse(it.mood) { "🙂" }, fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Детали выбранного дня
            selected?.let { day ->
                Spacer(Modifier.height(12.dp))
                Text(
                    day.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale("ru"))),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                workoutDays[day].orEmpty().forEach { w ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🏋️", fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${w.type} · ${w.durationMinutes} мин", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${timeOf(w.createdAt)} · +${w.points} очков", color = GrayText, fontSize = 13.sp)
                        }
                    }
                }
                moodDays[day].orEmpty().forEach { m ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(faces.getOrElse(m.mood) { "🙂" }, fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(timeOf(m.createdAt), color = GrayText, fontSize = 13.sp)
                            if (!m.note.isNullOrBlank()) Text(m.note, fontSize = 14.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = GrayText, fontSize = 12.sp)
    }
}

@Composable
private fun MenuRow(emoji: String, title: String, sub: String, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(sub, color = GrayText, fontSize = 13.sp)
            }
            Text("→", color = GrayText, fontSize = 16.sp)
        }
    }
}
