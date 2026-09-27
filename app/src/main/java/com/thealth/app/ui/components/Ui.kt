package com.thealth.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealth.app.ui.theme.GrayBg
import com.thealth.app.ui.theme.GrayText
import com.thealth.app.ui.theme.Ink
import com.thealth.app.ui.theme.Yellow

fun interestUi(name: String): Pair<String, String> = when (name) {
    "Running" -> "🏃" to "Бег"
    "Fitness" -> "🧘" to "Фитнес"
    "TherapeuticExercise" -> "🩺" to "ЛФК"
    "Bicycling" -> "🚲" to "Велосипед"
    "Swimming" -> "🏊" to "Плавание"
    "WeightTraining" -> "🏋️" to "Силовые тренировки"
    "Skiing" -> "⛷️" to "Лыжи"
    "MartialArts" -> "🥊" to "Единоборства"
    "Chess" -> "♟️" to "Шахматы"
    "Volleyball" -> "🏐" to "Волейбол"
    "Football" -> "⚽" to "Футбол"
    "Tennis" -> "🎾" to "Теннис"
    "Basketball" -> "🏀" to "Баскетбол"
    "Hockey" -> "🏒" to "Хоккей"
    else -> "✨" to name
}

fun fmt(n: Number): String = "%,d".format(n.toLong()).replace(',', ' ')

fun contentEmoji(type: String): String = when (type.lowercase()) {
    "meditation" -> "🧘"
    "course" -> "📚"
    "recipe" -> "🥗"
    "article" -> "📄"
    else -> "✨"
}

fun metricEmoji(metric: String): String = when (metric) {
    "Steps" -> "🏃"
    "WorkoutMinutes" -> "⏱"
    "WorkoutCount" -> "💪"
    else -> "🏆"
}

@Composable
fun YellowButton(
    label: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick, enabled = enabled && !loading,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Ink, disabledContainerColor = GrayBg)
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Ink, strokeWidth = 2.5.dp)
        else Text(label, fontWeight = FontWeight.Bold, fontSize = fontSize)
    }
}

@Composable
fun ProgressBar(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(if (i < step) Yellow else GrayBg))
        }
    }
}

@Composable
fun PCard(emoji: String, title: String, desc: String, on: Boolean, set: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = GrayBg), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(top = 12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("$emoji  $title", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = set,
                    colors = SwitchDefaults.colors(checkedTrackColor = Yellow, checkedThumbColor = Color.White))
            }
            Text(desc, color = GrayText, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 22.dp, bottom = 12.dp))
}

@Composable
fun Bar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(8.dp).clip(RoundedCornerShape(4.dp)).background(GrayBg)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(8.dp)
            .clip(RoundedCornerShape(4.dp)).background(Yellow))
    }
}

@Composable
fun Avatar(letter: String, dark: Boolean = false, big: Boolean = false) {
    Box(
        Modifier.size(if (big) 72.dp else 36.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (dark) Ink else Yellow),
        contentAlignment = Alignment.Center
    ) {
        Text(letter.uppercase(), color = if (dark) Yellow else Ink,
            fontWeight = FontWeight.ExtraBold, fontSize = if (big) 24.sp else 13.sp)
    }
}

// Кольцо шагов за сегодня. Цель дневная, по умолчанию 10 000.
@Composable
fun StepsRing(steps: Int?, goal: Int = 10_000) {
    val progress = if (steps == null) 0f else (steps.toFloat() / goal).coerceIn(0f, 1f)
    Card(colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 14.dp.toPx()
                    drawArc(GrayBg, -90f, 360f, false, style = Stroke(w, cap = StrokeCap.Round))
                    drawArc(Yellow, -90f, 360f * progress, false, style = Stroke(w, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (steps == null) "…" else fmt(steps),
                        fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text("шагов из ${fmt(goal)}", color = GrayText, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AppCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0F0F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) { content() }
}

@Composable
fun EmptyHint(text: String) {
    Text(text, color = GrayText, fontSize = 13.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp))
}
