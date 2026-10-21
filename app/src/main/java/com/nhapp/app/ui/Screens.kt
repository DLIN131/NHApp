package com.nhapp.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhapp.app.Prefs
import com.nhapp.app.data.Library
import com.nhapp.app.net.Api
import com.nhapp.app.net.GallerySummary
import com.nhapp.app.util.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 加上語言過濾；使用者自己寫了 language: 就不動 */
fun withLanguage(query: String): String {
    val lang = Prefs.language
    if (lang.isEmpty() || query.contains("language:")) return query.ifBlank { "pages:>0" }
    return "$query language:$lang".trim()
}

/** 首頁的列表狀態，整個 App 共用，切分頁不會重新載入 */
object Home {
    lateinit var scope: CoroutineScope
        private set
    lateinit var latest: Feed
        private set
    lateinit var popular: Feed
        private set
    var popularSort by mutableStateOf("popular-week")
    var popularNow by mutableStateOf<List<GallerySummary>>(emptyList())
    var tab by mutableIntStateOf(0)

    fun init(scope: CoroutineScope) {
        if (::scope.isInitialized) return
        this.scope = scope
        rebuild()
    }

    /** 語言設定改了之後重建 */
    fun rebuild() {
        latest = Feed(scope) { page ->
            if (Prefs.language.isEmpty()) Api.latest(page) else Api.search(withLanguage(""), "date", page)
        }
        popular = Feed(scope) { page -> Api.search(withLanguage(""), popularSort, page) }
        popularNow = emptyList()
        scope.launch { runCatching { popularNow = Api.popularNow() } }
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        SearchBar(
            onRandom = {
                scope.launch {
                    runCatching { Api.random() }
                        .onSuccess { Nav.openGallery(it) }
                        .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
                }
            },
        )
        Box(Modifier.weight(1f)) {
            when (Home.tab) {
                0 -> FeedGrid(Home.latest, header = {
                    fullWidth("categories") { CategoryEntryRow() }
                    if (Home.popularNow.isNotEmpty()) {
                        fullWidth("now") { PopularNowRow(Home.popularNow) }
                        fullWidth("latestTitle") { SectionTitle("最新上傳") }
                    }
                })
                1 -> FeedGrid(Home.popular, header = {
                    fullWidth("sorts") {
                        SortChips(Home.popularSort, onSelect = { s ->
                            if (s != Home.popularSort) {
                                Home.popularSort = s
                                Home.popular.refresh()
                            }
                        }, hideDate = true)
                    }
                })
                2 -> BookshelfScreen()
                3 -> LocalList(Library.favorites, "還沒有收藏，在作品頁按愛心加入")
                4 -> LocalList(Library.history, "還沒有閱讀紀錄", onClear = { Library.clearHistory() })
            }
        }
        BottomTabs()
    }
}

@Composable
private fun SearchBar(onRandom: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    fun submit() {
        val q = text.trim()
        if (q.isEmpty()) return
        focus.clearFocus()
        // 純數字視為作品編號
        if (q.all { it.isDigit() } && q.length <= 7) Nav.openGallery(q.toInt())
        else Nav.push(Route.Results(q, q, null, "date"))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text("搜尋標題、標籤或編號") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(Icons.Filled.Clear, "清除") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
                focusedIndicatorColor = Accent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f).height(52.dp),
        )
        IconButton(onClick = onRandom) { Text("🎲", fontSize = 20.sp) }
        IconButton(onClick = { Nav.push(Route.Settings) }) { Icon(Icons.Filled.Settings, "設定") }
    }
}

@Composable
private fun BottomTabs() {
    val tabs = listOf<Pair<String, ImageVector>>(
        "首頁" to Icons.Filled.Home,
        "熱門" to Icons.Filled.Star,
        "書櫃" to Icons.Filled.Menu,
        "收藏" to Icons.Filled.Favorite,
        "紀錄" to Icons.Filled.Refresh,
    )
    NavigationBar(containerColor = Surface) {
        tabs.forEachIndexed { i, (label, icon) ->
            NavigationBarItem(
                selected = Home.tab == i,
                onClick = {
                    if (Home.tab == i) {
                        // 再點一次同一個分頁：回到頂端
                        when (i) {
                            0 -> Home.latest.refresh()
                            1 -> Home.popular.refresh()
                        }
                    }
                    Home.tab = i
                },
                icon = { Icon(icon, label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Accent.copy(alpha = 0.35f)),
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(4.dp))
}

@Composable
private fun PopularNowRow(list: List<GallerySummary>) {
    Column {
        SectionTitle("現在熱門")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(list, key = { it.id }) { g -> GalleryCard(g, Modifier.width(130.dp)) { Nav.openGallery(g) } }
        }
    }
}

@Composable
private fun LocalList(list: List<GallerySummary>, empty: String, onClear: (() -> Unit)? = null) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(empty, color = Color.Gray) }
        return
    }
    var confirm by remember { mutableStateOf(false) }
    GalleryGrid(list, rememberLazyGridState(), header = {
        if (onClear != null) fullWidth("clear") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("共 ${list.size} 本", color = Color.Gray, modifier = Modifier.weight(1f).padding(start = 4.dp))
                TextButton(onClick = { confirm = true }) { Text("清除紀錄") }
            }
        }
    })
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("清除閱讀紀錄？") },
            text = { Text("閱讀紀錄和每本的閱讀進度都會刪掉，收藏不受影響。") },
            confirmButton = { TextButton(onClick = { onClear?.invoke(); confirm = false }) { Text("清除") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
        )
    }
}

@Composable
fun TopBar(title: String, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { Nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = Color.White,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun ResultsScreen(route: Route.Results, scope: CoroutineScope) {
    val feed = route.feed ?: Feed(scope) { page ->
        // 有語言過濾時標籤頁改走搜尋，才能同時套用語言
        if (route.tagId != null && Prefs.language.isEmpty()) Api.tagged(route.tagId, route.sort, page)
        else Api.search(withLanguage(route.query.orEmpty()), route.sort, page)
    }.also { route.feed = it }
    Column(Modifier.fillMaxSize()) {
        TopBar(route.title)
        FeedGrid(feed, header = {
            fullWidth("sorts") {
                SortChips(route.sort, onSelect = { s ->
                    if (s != route.sort) {
                        route.sort = s
                        feed.refresh()
                    }
                })
            }
        })
    }
}

@Composable
fun SettingsScreen() {
    var language by remember { mutableStateOf(Prefs.language) }
    var jpTitle by remember { mutableStateOf(Prefs.japaneseTitle) }
    var vertical by remember { mutableStateOf(Prefs.vertical) }
    var rtl by remember { mutableStateOf(Prefs.rightToLeft) }
    Column(Modifier.fillMaxSize()) {
        TopBar("設定")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("語言過濾", fontWeight = FontWeight.Bold, color = Color.White)
            Text("首頁、熱門、搜尋都只顯示這個語言", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("" to "全部", "chinese" to "中文", "japanese" to "日文", "english" to "英文").forEach { (k, label) ->
                    Chip(label, language == k) {
                        if (language != k) {
                            language = k
                            Prefs.language = k
                            Home.rebuild()
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            SwitchRow("標題顯示日文", "預設顯示英文（羅馬拼音）標題", jpTitle) { jpTitle = it; Prefs.japaneseTitle = it }
            SwitchRow("上下捲動閱讀", "關閉則改成左右翻頁", vertical) { vertical = it; Prefs.vertical = it }
            SwitchRow("由右往左翻頁", "左右翻頁時使用日漫順序", rtl) { rtl = it; Prefs.rightToLeft = it }
            Spacer(Modifier.height(20.dp))
            Text("搜尋小技巧", fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "• 輸入數字直接開啟該編號\n" +
                    "• tag:\"big breasts\"、artist:xxx、parody:xxx\n" +
                    "• 前面加 - 排除，例如 -tag:yaoi\n" +
                    "• pages:>20 頁數大於 20",
                color = Color.Gray,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(20.dp))
            UpdateRow()
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White)
            Text(sub, color = Color.Gray, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 手動檢查更新；有新版就交給主畫面的更新視窗（手動檢查時不管「略過這個版本」） */
@Composable
private fun UpdateRow() {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !checking) {
                checking = true
                status = "檢查中…"
                scope.launch {
                    status = try {
                        val r = Updater.check()
                        if (r != null) {
                            Updater.pending = r
                            "有新版本 ${r.name.removePrefix("NHApp ")}"
                        } else "已經是最新版"
                    } catch (e: Exception) {
                        "檢查失敗：${e.message}"
                    }
                    checking = false
                }
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("檢查更新", color = Color.White)
            Text("目前版本 ${Updater.currentVersionName}", color = Color.Gray, fontSize = 12.sp)
        }
        status?.let { Text(it, color = if (it.startsWith("有新版本")) Accent else Color.Gray, fontSize = 13.sp) }
    }
}
