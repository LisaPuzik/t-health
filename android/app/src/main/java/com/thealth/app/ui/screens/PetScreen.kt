package com.thealth.app.ui.screens

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealth.app.data.ApiCache
import com.thealth.app.data.ApiClient
import com.thealth.app.data.AuthStore
import com.thealth.app.data.PetDto
import com.thealth.app.ui.components.AppCard
import com.thealth.app.ui.components.fmt
import com.thealth.app.ui.theme.GrayBg
import com.thealth.app.ui.theme.GrayText
import com.thealth.app.ui.theme.Ink
import com.thealth.app.ui.theme.Yellow

// Экран тамагочи-кристалла по макету: hero с кристаллом, бар уровня, стадии, достижения
@Composable
fun PetScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var pet by remember { mutableStateOf<PetDto?>(null) }

    LaunchedEffect(Unit) {
        try { pet = ApiCache.get("pet") { ApiClient.service(AuthStore(ctx)).pet() } }
        catch (_: Exception) { }
    }

    LazyColumn(Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        item {
            // Hero 400dp с градиентом
            Box(Modifier.fillMaxWidth().height(400.dp)
                .background(Brush.linearGradient(
                    listOf(Color(0xFF2A1B5E), Color(0xFF4C1D95), Color(0xFF831843))
                ))) {
                // Звёзды
                Canvas(Modifier.fillMaxSize()) {
                    val pts = listOf(
                        0.18f to 0.12f, 0.78f to 0.22f, 0.12f to 0.68f,
                        0.82f to 0.80f, 0.08f to 0.40f, 0.90f to 0.35f
                    )
                    pts.forEach { (x, y) ->
                        drawCircle(Color.White.copy(alpha = 0.7f), 2.5f, Offset(size.width * x, size.height * y))
                    }
                }
                // Кнопка назад поверх
                Box(Modifier.padding(start = 20.dp, top = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable { onBack() }.padding(10.dp)) {
                    Text("←", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                // Кристалл с парением
                val float by rememberInfiniteTransition(label = "float").animateFloat(
                    0f, -12f,
                    InfiniteRepeatableSpec(tween(2000), RepeatMode.Reverse), label = "y"
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset(y = float.dp)) {
                        CrystalCanvas()
                    }
                }
                // Орбита
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(240.dp).border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape))
                }
                // Бейдж уровня
                Box(Modifier.fillMaxSize().padding(bottom = 20.dp), contentAlignment = Alignment.BottomCenter) {
                    Text("✨ Кристалл · Уровень ${pet?.level ?: "…"}", color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Твой кристалл растёт", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
                Text("Каждая тренировка делает его сильнее", color = GrayText, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

                // Бар уровня
                AppCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Уровень ${pet?.level ?: "…"}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${pet?.progressPct ?: 0}% до Ур. ${(pet?.level ?: 1) + 1}",
                                color = GrayText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(10.dp)
                            .clip(RoundedCornerShape(5.dp)).background(GrayBg)) {
                            Box(Modifier.fillMaxWidth((pet?.progressPct ?: 0) / 100f).height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Brush.horizontalGradient(
                                    listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))))
                        }
                        Text("Осталось ${pet?.workoutsToGo ?: "…"} трен. до следующего уровня · ⚡${pet?.energy ?: 0}",
                            color = GrayText, fontSize = 12.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                    }
                }

                // Статы
                Row(Modifier.padding(top = 4.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("🔥 ${pet?.stats?.streakDays ?: 0}", "дней подряд", Modifier.weight(1f))
                    StatCard("${pet?.stats?.workouts30 ?: 0}", "тренировок", Modifier.weight(1f))
                    StatCard(shortNum(pet?.stats?.steps30 ?: 0), "шагов", Modifier.weight(1f))
                }

                Text("Путь роста", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            }
        }

        // Стадии (горизонталь)
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp)) {
                items(pet?.stage?.stages ?: emptyList()) { s ->
                    val borderColor = when (s.state) {
                        "current" -> Color(0xFF8B5CF6)
                        "done" -> Color(0xFFE5E5E5)
                        else -> Color.Transparent
                    }
                    Column(
                        Modifier.width(88.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
                            .padding(12.dp, 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(s.icon, fontSize = 28.sp, modifier = Modifier.alpha(if (s.state == "locked") 0.45f else 1f))
                        Text("Ур. ${s.level}", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp))
                        Text(if (s.state == "current") "Сейчас" else s.name,
                            fontSize = 10.sp, color = GrayText)
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Достижения", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
            }
        }

        val ms = pet?.milestones ?: emptyList()
        if (ms.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ink)
                }
            }
        } else {
            items(ms) { m ->
                Row(Modifier.padding(horizontal = 20.dp)) {
                    AppCard {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (m.done) Yellow else GrayBg),
                                contentAlignment = Alignment.Center) {
                                Text(if (m.done) "🏅" else "○", fontSize = 20.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(m.detail, fontSize = 12.sp, color = GrayText)
                            }
                            Text(if (m.done) "✓" else "○",
                                color = if (m.done) Color(0xFF22C55E) else Color(0xFFCBD5E1),
                                fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

private fun shortNum(n: Int): String = when {
    n >= 1000 -> "${n / 1000}k"
    else -> "$n"
}

// Упрощённый кристалл из макета: шестигранник с гранями и бликом
@Composable
private fun CrystalCanvas() {
    Canvas(Modifier.size(180.dp)) {
        val cx = size.width / 2
        val w = size.width
        val top = Offset(cx, 12f)
        val rightTop = Offset(cx + w * 0.32f, size.height * 0.33f)
        val rightBot = Offset(cx + w * 0.32f, size.height * 0.69f)
        val bottom = Offset(cx, size.height - 12f)
        val leftBot = Offset(cx - w * 0.32f, size.height * 0.69f)
        val leftTop = Offset(cx - w * 0.32f, size.height * 0.33f)
        val mid = Offset(cx, size.height * 0.5f)

        fun facet(pts: List<Offset>, color: Color) {
            drawPath(Path().apply {
                moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }, color)
        }
        facet(listOf(top, rightTop, rightBot, bottom, leftBot, leftTop), Color(0xFF8B5CF6))
        facet(listOf(top, mid, leftTop), Color(0xFFC4B5FD))
        facet(listOf(top, rightTop, mid), Color(0xFFEC4899))
        facet(listOf(mid, leftTop, leftBot, bottom), Color(0xFF7C3AED))
        facet(listOf(mid, rightTop, rightBot, bottom), Color(0xFFA855F7))
        // блик
        facet(listOf(top, Offset(cx, size.height * 0.33f), Offset(cx - w * 0.17f, size.height * 0.23f)),
            Color.White.copy(alpha = 0.55f))
        // контур
        drawPath(Path().apply {
            moveTo(top.x, top.y); lineTo(rightTop.x, rightTop.y)
            lineTo(rightBot.x, rightBot.y); lineTo(bottom.x, bottom.y)
            lineTo(leftBot.x, leftBot.y); lineTo(leftTop.x, leftTop.y); close()
        }, Color(0xFFF5D0FE), style = Stroke(2f))
    }
}

@Composable
private fun StatCard(num: String, lbl: String, modifier: Modifier = Modifier) {    AppCard {
        Column(modifier.padding(14.dp, 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(num, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(lbl, fontSize = 11.sp, color = GrayText)
        }
    }
}
