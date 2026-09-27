package com.thealth.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealth.app.data.ApiCache
import com.thealth.app.data.ApiClient
import com.thealth.app.data.AuthStore
import com.thealth.app.data.CreateCommentRequest
import com.thealth.app.data.CreatePostRequest
import com.thealth.app.data.FeedCommentDto
import com.thealth.app.data.FeedPostDto
import com.thealth.app.ui.components.AppCard
import com.thealth.app.ui.components.Avatar
import com.thealth.app.ui.components.EmptyHint
import com.thealth.app.ui.components.YellowButton
import com.thealth.app.ui.theme.GrayBg
import com.thealth.app.ui.theme.GrayText
import com.thealth.app.ui.theme.Ink
import com.thealth.app.ui.theme.Yellow
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

// Лента новостей (колокольчик на главной). Категории и посты — с бэка api/feed.
private val FeedSegments = listOf(
    "Всё" to null,
    "Рецепты" to "Recipe",
    "Тренировки" to "Workout",
    "Достижения" to "Achievement"
)

private fun teamRu(team: String?): String = when (team) {
    "Engineering" -> "разработка"
    "Marketing" -> "маркетинг"
    null -> "без команды"
    else -> team
}

private fun plural(n: Long, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1L && m100 != 11L -> one
        m10 in 2..4 && (m100 < 12 || m100 > 14) -> few
        else -> many
    }
}

private fun feedTimeAgo(iso: String): String {
    return try {
        val mins = ChronoUnit.MINUTES.between(Instant.parse(iso), Instant.now())
        if (mins < 0) return ""
        when {
            mins < 1 -> "только что"
            mins < 60 -> "$mins ${plural(mins, "минуту", "минуты", "минут")} назад"
            mins < 24 * 60 -> {
                val h = mins / 60
                "$h ${plural(h, "час", "часа", "часов")} назад"
            }
            mins < 48 * 60 -> "Вчера"
            mins < 7 * 24 * 60 -> {
                val d = mins / (24 * 60)
                "$d ${plural(d, "день", "дня", "дней")} назад"
            }
            else -> {
                val d = Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                d.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale("ru")))
            }
        }
    } catch (_: Exception) { "" }
}

private fun postGradient(type: String): Pair<Color, Color> = when (type) {
    "Recipe" -> Color(0xFFFFF3B0) to Yellow
    "Workout" -> Color(0xFFE0E0E0) to Color(0xFFF5F5F5)
    "Achievement" -> Yellow to Color(0xFFE8C700)
    else -> GrayBg to Color(0xFFE8E8E8)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<FeedPostDto>>(emptyList()) }
    var seg by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var commentsPost by remember { mutableStateOf<FeedPostDto?>(null) }

    fun load() {
        scope.launch {
            loading = true
            err = null
            try {
                val store = AuthStore(ctx)
                posts = ApiClient.service(store).feed(type = FeedSegments[seg].second).items
                store.setLastFeedSeenMs()
                ApiCache.invalidate("newsBadge")
            } catch (e: Exception) {
                err = e.message ?: "Не загрузилось"
            }
            loading = false
        }
    }
    LaunchedEffect(seg) { load() }

    fun toggleLike(p: FeedPostDto) {
        scope.launch {
            try {
                val r = ApiClient.service(AuthStore(ctx)).toggleLike(p.id)
                posts = posts.map {
                    if (it.id == p.id) it.copy(likedByMe = r.liked, likesCount = r.likesCount) else it
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "Не вышло", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun share(p: FeedPostDto) {
        try {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, p.content)
            }
            ctx.startActivity(Intent.createChooser(i, "Поделиться"))
        } catch (_: Exception) { }
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            // Header: назад | Лента | фильтр
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 44.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(GrayBg)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    "Лента", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(GrayBg)
                        .clickable {
                            Toast.makeText(ctx, "Категории — чуть ниже", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("☰", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Сегмент-категории (скролл — 4 штуки не влезают в ряд)
            Box(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(40.dp)
                    .clip(RoundedCornerShape(12.dp)).background(GrayBg).padding(4.dp)
            ) {
                androidx.compose.foundation.lazy.LazyRow(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(FeedSegments.size) { i ->
                        val (label, _) = FeedSegments[i]
                        val active = i == seg
                        Box(
                            Modifier.fillParentMaxHeight().widthIn(min = 86.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) Yellow else Color.Transparent)
                                .clickable { seg = i },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label, fontSize = 13.sp, maxLines = 1,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (active) Ink else GrayText,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                        }
                    }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ink)
                }
            } else if (err != null) {
                Column(
                    Modifier.fillMaxSize().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(err!!, color = GrayText, fontSize = 14.sp)
                    TextButton(onClick = { load() }) { Text("Повторить", color = Ink) }
                }
            } else if (posts.isEmpty()) {
                EmptyHint("Пока тихо — стань первым ✍️")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item { Spacer(Modifier.height(16.dp)) }
                    items(posts, key = { it.id }) { p ->
                        Row(Modifier.padding(horizontal = 20.dp)) {
                            PostCard(
                                p = p,
                                onLike = { toggleLike(p) },
                                onComments = { commentsPost = p },
                                onShare = { share(p) }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }

        // FAB создания поста
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp)
                .size(56.dp).clip(CircleShape).background(Yellow)
                .clickable { showCreate = true },
            contentAlignment = Alignment.Center
        ) {
            Text("+", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        }
    }

    if (showCreate) CreatePostSheet(
        onClose = { showCreate = false },
        onPosted = { if (seg != 0) seg = 0 else load() }
    )

    commentsPost?.let { p ->
        CommentsSheet(
            post = p,
            onClose = { commentsPost = null },
            onCountChanged = { delta ->
                posts = posts.map {
                    if (it.id == p.id) it.copy(commentsCount = (it.commentsCount + delta).coerceAtLeast(0)) else it
                }
            }
        )
    }
}

@Composable
private fun PostCard(p: FeedPostDto, onLike: () -> Unit, onComments: () -> Unit, onShare: () -> Unit) {
    AppCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.authorName.firstOrNull()?.toString() ?: "?", dark = p.id % 2 == 0)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${p.authorName}, ${teamRu(p.authorTeam)}",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                    Text(feedTimeAgo(p.createdAt), color = GrayText, fontSize = 12.sp)
                }
            }
            Text(p.content, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 12.dp))
            if (p.imageEmoji != null) {
                val (c1, c2) = postGradient(p.postType)
                Box(
                    Modifier.padding(top = 12.dp).fillMaxWidth().height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(c1, c2))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(p.imageEmoji, fontSize = 64.sp)
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    "👍 ${p.likesCount}", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (p.likedByMe) Ink else GrayText,
                    modifier = Modifier.clickable { onLike() }
                )
                Text(
                    "💬 ${p.commentsCount}", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = GrayText,
                    modifier = Modifier.clickable { onComments() }
                )
                Text(
                    "↗ Поделиться", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = GrayText,
                    modifier = Modifier.clickable { onShare() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePostSheet(onClose: () -> Unit, onPosted: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var typeIdx by remember { mutableStateOf(0) }
    var emoji by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var cerr by remember { mutableStateOf<String?>(null) }
    val types = listOf("Общее" to "General", "Рецепт" to "Recipe", "Тренировка" to "Workout", "Достижение" to "Achievement")
    val emojis: List<String?> = listOf(null, "🥗", "🏃", "🏆", "🧘", "💪", "🥑")

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(20.dp)) {
            Text("Новый пост", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                types.forEachIndexed { i, (label, _) ->
                    val sel = i == typeIdx
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Yellow else GrayBg)
                            .clickable { typeIdx = i }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Картинка", color = GrayText, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                emojis.forEach { e ->
                    val sel = e == emoji
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Yellow else GrayBg)
                            .clickable { emoji = e },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(e ?: "—", fontSize = 20.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 1000) text = it },
                label = { Text("Что нового?") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            if (cerr != null) Text(cerr!!, color = Color.Red, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            YellowButton("Опубликовать", loading = busy, enabled = text.isNotBlank()) {
                scope.launch {
                    busy = true
                    cerr = null
                    try {
                        ApiClient.service(AuthStore(ctx)).createPost(
                            CreatePostRequest(text.trim(), types[typeIdx].second, emoji)
                        )
                        onClose()
                        onPosted()
                    } catch (e: Exception) {
                        cerr = e.message
                    }
                    busy = false
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(post: FeedPostDto, onClose: () -> Unit, onCountChanged: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember(post.id) { mutableStateOf<List<FeedCommentDto>>(emptyList()) }
    var cloading by remember(post.id) { mutableStateOf(true) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(post.id) {
        try {
            list = ApiClient.service(AuthStore(ctx)).comments(post.id).items
        } catch (_: Exception) { }
        cloading = false
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(20.dp)) {
            Text("Комментарии", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (cloading) {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ink, modifier = Modifier.size(28.dp))
                }
            } else if (list.isEmpty()) {
                Text("Пока тихо — стань первым 💬", color = GrayText, fontSize = 14.sp)
            } else {
                list.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.authorName.firstOrNull()?.toString() ?: "?")
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.authorName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(c.content, fontSize = 14.sp)
                            Text(feedTimeAgo(c.createdAt), color = GrayText, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 500) draft = it },
                    label = { Text("Комментарий") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Yellow)
                        .clickable(enabled = draft.isNotBlank() && !busy) {
                            scope.launch {
                                busy = true
                                try {
                                    val added = ApiClient.service(AuthStore(ctx))
                                        .addComment(post.id, CreateCommentRequest(draft.trim()))
                                    list = list + added
                                    draft = ""
                                    onCountChanged(1)
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, e.message ?: "Не вышло", Toast.LENGTH_SHORT).show()
                                }
                                busy = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("→", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
