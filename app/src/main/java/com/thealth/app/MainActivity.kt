package com.thealth.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.thealth.app.data.ApiClient
import com.thealth.app.data.AuthStore
import com.thealth.app.data.InterestDto
import com.thealth.app.data.LoginRequest
import com.thealth.app.data.RegisterRequest
import com.thealth.app.data.SetInterestsRequest
import com.thealth.app.data.friendlyError
import com.thealth.app.data.hasActivityPermission
import com.thealth.app.ui.components.ProgressBar
import com.thealth.app.ui.components.PCard
import com.thealth.app.ui.components.YellowButton
import com.thealth.app.ui.components.interestUi
import com.thealth.app.ui.screens.MainScaffold
import com.thealth.app.ui.theme.GrayBg
import com.thealth.app.ui.theme.GrayText
import com.thealth.app.ui.theme.Ink
import com.thealth.app.ui.theme.THealthTheme
import com.thealth.app.ui.theme.Yellow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            THealthTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "splash") {
                    composable("splash") { SplashScreen { dest -> nav.navigate(dest) { popUpTo("splash") { inclusive = true } } } }
                    composable("login") {
                        LoginScreen(onDone = { isNew ->
                            nav.navigate(if (isNew) "interests" else "main") {
                                popUpTo("login") { inclusive = true }
                            }
                        })
                    }
                    composable("interests") { InterestsScreen(onNext = { nav.navigate("privacy") }, onSkip = { nav.navigate("privacy") }) }
                    composable("privacy") { PrivacyScreen(onDone = { nav.navigate("main") { popUpTo("splash") { inclusive = true } } }) }
                    composable("main") { MainScaffold(onLogout = { nav.navigate("login") { popUpTo("splash") { inclusive = true } } }) }
                }
            }
        }
    }
}

// ---------- SPLASH ----------
@Composable
fun SplashScreen(go: (String) -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1200)
        val store = AuthStore(ctx)
        // Вход — сразу на главную; интересы только у fresh-регистрации
        go(if (store.access() != null) "main" else "login")
    }
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Yellow), contentAlignment = Alignment.Center) {
                Text("∿", fontSize = 38.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            Text("THealth", color = Yellow, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Text("Твоё здоровье. Твои правила.", color = Yellow.copy(alpha = 0.7f), fontSize = 13.sp)
        }
    }
}

// ---------- LOGIN ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onDone: (isNew: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<String?>(null) } // "auth" | "notid"
    var mode by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    fun isEmailOk(s: String) =
        android.util.Patterns.EMAIL_ADDRESS.matcher(s.trim()).matches()

    fun submit() {
        // Проверка до сети — конкретные подсказки вместо 400
        err = when {
            !isEmailOk(email) -> "Проверь email — похоже, там опечатка"
            password.isEmpty() -> "Введи пароль"
            mode == "register" && password.length < 6 -> "Пароль — минимум 6 символов"
            mode == "register" && first.isBlank() -> "Как тебя зовут? Введи имя"
            mode == "register" && last.isBlank() -> "Введи фамилию"
            else -> null
        }
        if (err != null) return
        scope.launch {
            busy = true; err = null
            try {
                val store = AuthStore(ctx)
                val api = ApiClient.public(store)
                val device = store.deviceId()
                val auth = if (mode == "login")
                    api.login(LoginRequest(email.trim(), password, device))
                else
                    api.register(RegisterRequest(email.trim(), password, first.trim(), last.trim(), null, device))
                store.saveSession(auth)
                sheet = null
                val isNew = mode == "register"
                // Запрос доступа к Health — один раз на Главной, здесь не мешаем входу
                if (!isNew) store.setOnboarded()
                onDone(isNew)
            } catch (e: Exception) {
                err = friendlyError(e)
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(50.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Ink), contentAlignment = Alignment.Center) {
                Text("∿", fontSize = 12.sp, color = Yellow)
            }
            Spacer(Modifier.width(6.dp))
            Text("T-Health", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        Spacer(Modifier.weight(1f))
        Text("Добро пожаловать\nв T-Health", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), lineHeight = 32.sp)
        Spacer(Modifier.height(32.dp))
        YellowButton("Войти через T-ID") { sheet = "auth" }
        Spacer(Modifier.height(14.dp))
        Text("Продолжая, ты соглашаешься с политикой обработки данных.",
            color = GrayText, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Text("У меня нет T-ID", modifier = Modifier.fillMaxWidth().clickable { sheet = "notid" },
            textAlign = TextAlign.Center, fontSize = 13.sp)
        Spacer(Modifier.height(26.dp))
    }

    if (sheet != null) ModalBottomSheet(onDismissRequest = { sheet = null }) {
        Column(Modifier.padding(20.dp)) {
            if (sheet == "notid") {
                Text("Нет доступа?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Обратись в HR-отдел или IT-поддержку — тебе помогут получить T-ID.",
                    color = GrayText, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                YellowButton("Написать в поддержку") { sheet = null }
                TextButton(onClick = { sheet = null }, modifier = Modifier.fillMaxWidth()) { Text("Закрыть", color = GrayText) }
            } else {
                Text(if (mode == "login") "Вход по T-ID" else "Регистрация", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Демо-режим: T-ID заменен email+паролем напрямую к WellnessApp.Api.",
                    color = GrayText, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                if (mode == "register") {
                    OutlinedTextField(value = first, onValueChange = { first = it }, label = { Text("Имя") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = last, onValueChange = { last = it }, label = { Text("Фамилия") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Пароль") }, modifier = Modifier.fillMaxWidth())
                if (err != null) Text(err!!, color = Color.Red, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(16.dp))
                YellowButton(if (mode == "login") "Войти" else "Создать аккаунт", loading = busy) { submit() }
                TextButton(onClick = { mode = if (mode == "login") "register" else "login" }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (mode == "login") "Нет аккаунта? Регистрация" else "Есть аккаунт? Войти", color = GrayText)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ---------- INTERESTS ----------
@Composable
fun InterestsScreen(onNext: () -> Unit, onSkip: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<InterestDto>>(emptyList()) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val store = AuthStore(ctx)
            items = ApiClient.service(store).interests()
            picked = items.filter { it.selected }.map { it.id }.toSet()
        } catch (_: Exception) { } finally { loading = false }
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            ProgressBar(1)
            Spacer(Modifier.height(20.dp))
            Text("Что тебе интересно?", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("Выбери минимум 3 направления — будем рекомендовать челленджи и сообщества.",
                color = GrayText, fontSize = 14.sp)
        }
        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Ink)
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items) { it ->
                    val (emoji, label) = interestUi(it.name)
                    val sel = picked.contains(it.id)
                    Card(
                        onClick = { picked = if (sel) picked - it.id else picked + it.id },
                        colors = CardDefaults.cardColors(containerColor = if (sel) Yellow else GrayBg),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(emoji, fontSize = 24.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(20.dp)) {
            YellowButton("Далее", enabled = picked.size >= 3, loading = busy) {
                scope.launch {
                    busy = true
                    try {
                        ApiClient.service(AuthStore(ctx)).setInterests(SetInterestsRequest(picked.toList()))
                        onNext()
                    } catch (_: Exception) { busy = false }
                }
            }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Пропустить", color = GrayText) }
        }
    }
}

// ---------- PRIVACY ----------
@Composable
fun PrivacyScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var activity by remember { mutableStateOf(true) }
    var mood by remember { mutableStateOf(false) }
    var sync by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    // Штатный диалог ACTIVITY_RECOGNITION — показывается всегда. Результат не ждём:
    // чтение сенсора всё равно происходит на Главной, там же видно итог.
    val activityLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        val (a, m, s) = AuthStore(ctx).privacy()
        activity = a; mood = m; sync = s
    }

    fun finish() {
        scope.launch {
            busy = true
            AuthStore(ctx).savePrivacy(activity, mood, sync)
            if (sync && !hasActivityPermission(ctx)) {
                try {
                    activityLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                } catch (_: Exception) { }
            }
            AuthStore(ctx).setOnboarded()
            onDone()
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            ProgressBar(2)
            Spacer(Modifier.height(20.dp))
            Text("Ты решаешь,\nчто видят другие.", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
            Text("T-Health создан для твоей пользы, а не для слежки.", color = GrayText, fontSize = 14.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            PCard("🏃", "Моя активность для коллег", "Коллеги видят твои шаги и тренировки в ленте.", activity) { activity = it }
            PCard("🧠", "Моё настроение для HR", "HR видит только агрегированные тренды по отделу (от 5 человек).", mood) { mood = it }
            PCard("👣", "Подсчёт шагов с телефона", "Шаги идут в кольцо, стрики и челленджи. Без облаков.", sync) { sync = it }
            Card(colors = CardDefaults.cardColors(containerColor = Yellow), shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(top = 18.dp)) {
                Text("🔒 Твои данные защищены. HR никогда не увидит детали — только общие тренды.",
                    fontSize = 12.sp, modifier = Modifier.padding(14.dp))
            }
        }
        Column(Modifier.padding(20.dp)) {
            YellowButton("Далее", loading = busy) { finish() }
            TextButton(onClick = {
                scope.launch { AuthStore(ctx).savePrivacy(activity, mood, sync); AuthStore(ctx).setOnboarded(); onDone() }
            }, modifier = Modifier.fillMaxWidth()) { Text("Настрою позже", color = GrayText) }
        }
    }
}
