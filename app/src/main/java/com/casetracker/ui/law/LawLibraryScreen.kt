package com.casetracker.ui.law

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.casetracker.ui.components.MiuiCard
import com.casetracker.ui.components.MiuiTextField
import com.casetracker.ui.components.MiuiTopAppBar

/** 已由全文版（LAW_BOOKS_FULL / LAW_BOOKS_FULL2）替代的要点版 id，避免重复 */
private val FULL_TEXT_IDS = setOf(
    "accident_procedure",
    "hefei_road_safety",
    "anhui_implementation",
    "anhui_ebike",
    "hefei_non_motor",
    "accident_work_standard",
    "administrative_case_procedure",
    "criminal_case_procedure",
    "anhui_penalty_discretion",
    "drunk_driving_opinion",
    "overload_over_speed_standard",
    "article52_reply"
)

private val ALL_LAWS =
    LAW_BOOKS_FULL + LAW_BOOKS_FULL2 + listOf(LAW_BOOK_VIOLATION_PROCEDURE) +
        LAW_BOOKS_EXTRA.filterNot { it.id in FULL_TEXT_IDS }

/** 单条命中条文（用于搜索结果直接展示）；approx=true 表示错字/漏字近似命中 */
private data class HitArticle(
    val book: LawBook,
    val article: LawArticle,
    val approx: Boolean = false
)

@Composable
fun LawLibraryScreen() {
    var selectedBook by remember { mutableStateOf<LawBook?>(null) }
    // 从列表搜索结果进入时携带检索词，用于自动定位并展开高亮命中条文
    var selectedHighlight by remember { mutableStateOf("") }
    val book = selectedBook
    if (book == null) {
        LawListScreen(onOpenBook = { law, query ->
            selectedBook = law
            selectedHighlight = query
        })
    } else {
        LawDetailScreen(
            book = book,
            highlight = selectedHighlight,
            onBack = {
                selectedBook = null
                selectedHighlight = ""
            }
        )
    }
}

@Composable
private fun LawListScreen(onOpenBook: (LawBook, String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    // 防抖后的实际检索词：停顿 180ms 后再扫描全文，避免逐字输入时主线程卡顿
    var effectiveQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            effectiveQuery = ""
        } else {
            kotlinx.coroutines.delay(180)
            effectiveQuery = query
        }
    }

    // 搜索：跨所有法规，直接命中条文（基于防抖后的 effectiveQuery）
    // v1.73：支持「法规名关键字 + 条号」直接命中（如：道路交通安全法 二十二条一 / 安全法实施条例 十九条一）；
    // 法规名单独命中时只展示法规卡（不再整部法规条文全部列出），条文按内容/标题/条号/名称+条号命中。
    // v1.87 模糊搜索：输入按空格/逗号/顿号拆成多个关键词（AND 全部命中）；
    // 关键词子串未命中时启用错字/漏字容错（编辑距离：2~4 字词允许错 1 字、5 字以上允许错 2 字），
    // 近似命中的条文/法规标注「近似匹配」并排在精确命中之后。
    val searchState = remember(effectiveQuery) {
        if (effectiveQuery.isBlank()) null
        else {
            val rawQ = effectiveQuery.trim()
            val ref = parseArticleRef(rawQ)
            val terms = if (ref != null) splitTerms(ref.nameQuery) else splitTerms(rawQ)
            val hits = ALL_LAWS.flatMap { law ->
                law.articles.mapNotNull { a ->
                    when (matchArticle(law, a, terms, ref)) {
                        MatchKind.EXACT -> HitArticle(law, a, approx = false)
                        MatchKind.APPROX -> HitArticle(law, a, approx = true)
                        MatchKind.NONE -> null
                    }
                }
            }.sortedBy { if (it.approx) 1 else 0 }
            val nameTerms = if (ref != null) splitTerms(ref.nameQuery) else splitTerms(rawQ)
            val bookHits = ALL_LAWS.mapNotNull { law ->
                var approx = false
                for (t in nameTerms) {
                    val exact = law.name.contains(t, ignoreCase = true)
                    val ap = !exact && fuzzyContains(law.name, t)
                    if (!exact && !ap) return@mapNotNull null
                    if (!exact) approx = true
                }
                BookHit(law, approx)
            }
            SearchResult(
                hits = hits,
                bookHits = bookHits,
                highlight = if (ref?.nameQuery?.isNotBlank() == true) ref.nameQuery else rawQ
            )
        }
    }
    val isSearching = query.isNotBlank() && effectiveQuery != query

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        MiuiTopAppBar(
            title = {
                Column {
                    Text("法律法规", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "道路交通事故处理相关法规（时效已核查至 2026 年 10 月）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                MiuiTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索法条全文/标题（空格拆词 · 支持错字容错）",
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (query.isBlank()) {
                // 未搜索：展示法规列表卡片
                items(ALL_LAWS, key = { it.id }) { law ->
                    LawCard(law = law, onClick = { onOpenBook(law, "") })
                }
            } else {
                // 搜索中：先展示命中法规（名称命中），再直接展示命中条文，无需再点进法规
                val sr = searchState
                if (sr == null || (sr.hits.isEmpty() && sr.bookHits.isEmpty())) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isSearching) "正在搜索…"
                                else "未找到与「$query」相关的法规或条文",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    if (sr.bookHits.isNotEmpty()) {
                        item {
                            Text(
                                "命中法规",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(sr.bookHits, key = { it.law.id }) { bh ->
                            LawCard(
                                law = bh.law,
                                approx = bh.approx,
                                onClick = { onOpenBook(bh.law, "") }
                            )
                        }
                    }
                    if (sr.hits.isNotEmpty()) {
                        item {
                            Text(
                                "共命中 ${sr.hits.size} 条相关条文",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(sr.hits, key = { it.book.id + it.article.no }) { hit ->
                            HitArticleCard(
                                hit = hit,
                                highlight = sr.highlight,
                                // 点击进入法规详情：自动定位并高亮命中条文
                                onOpenBook = { onOpenBook(hit.book, sr.highlight) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LawCard(
    law: LawBook,
    onClick: () -> Unit,
    approx: Boolean = false
) {
    MiuiCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Scale,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        law.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (approx) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "近似匹配",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFB54708),
                            modifier = Modifier
                                .background(Color(0xFFFFF0E0), MaterialTheme.shapes.small)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "共${law.articlesCount}条·${law.chapters}章",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "点击查看 →",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 搜索命中的单条条文卡片：显示法规名 + 条号 + 内容片段，关键词高亮 */
@Composable
private fun HitArticleCard(
    hit: HitArticle,
    highlight: String,
    onOpenBook: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    MiuiCard(onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hit.book.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = hit.article.no,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (hit.approx) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "近似匹配",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB54708),
                    modifier = Modifier
                        .background(Color(0xFFFFF0E0), MaterialTheme.shapes.small)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (expanded) "收起 ▲" else "展开全文 ▼",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = highlightText(
                if (expanded) hit.article.content
                else hit.article.content.take(120) + if (hit.article.content.length > 120) "……" else "",
                highlight
            ),
            style = MaterialTheme.typography.bodySmall,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "查看本法规全部条文 →",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onOpenBook() }
        )
    }
}

/** 法规详情页行：章节头 / 条文 */
private sealed interface LawRow {
    data class Chapter(val name: String, val count: Int) : LawRow
    data class Article(val article: LawArticle) : LawRow
}

@Composable
private fun LawDetailScreen(
    book: LawBook,
    highlight: String,
    onBack: () -> Unit
) {
    // 二级页面返回只关闭法规详情，不弹退出确认
    BackHandler { onBack() }

    // 页内搜索框检索词
    var innerQuery by rememberSaveable(book.id) { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun matches(a: LawArticle, q: String): Boolean =
        q.isNotBlank() && (a.no.contains(q) || a.title.contains(q, ignoreCase = true) || a.content.contains(q, ignoreCase = true))

    // 第一级只显示条号/标题（不显示条文全文）；点击某条进入第二级：该条条文全文
    var selectedArticle by remember(book.id) { mutableStateOf<LawArticle?>(null) }
    selectedArticle?.let { article ->
        ArticleDetailScreen(
            book = book,
            article = article,
            highlight = if (innerQuery.isNotBlank()) innerQuery else highlight,
            onBack = { selectedArticle = null }
        )
        return
    }

    val hasChapters = book.chaptersData.isNotEmpty()
    val filteredArticles = remember(book.id, innerQuery) {
        if (innerQuery.isBlank()) book.articles else book.articles.filter { matches(it, innerQuery) }
    }

    // 扁平化行列表：有章节数据 → 按章分组；否则平铺
    val rows: List<LawRow> = remember(book.id, filteredArticles) {
        if (hasChapters) {
            buildList<LawRow> {
                for (ch in book.chaptersData) {
                    val chArticles = filteredArticles.filter {
                        chineseArticleNoToInt(it.no) in ch.from..ch.to
                    }
                    if (chArticles.isNotEmpty()) {
                        add(LawRow.Chapter(ch.name, chArticles.size))
                        addAll(chArticles.map { LawRow.Article(it) })
                    }
                }
                // 章节范围外的条文兜底
                val mappedNos = book.chaptersData.flatMap { (it.from..it.to).toList() }.toSet()
                val rest = filteredArticles.filter { chineseArticleNoToInt(it.no) !in mappedNos }
                if (rest.isNotEmpty()) {
                    add(LawRow.Chapter("其他条文", rest.size))
                    addAll(rest.map { LawRow.Article(it) })
                }
            }
        } else {
            filteredArticles.map { LawRow.Article(it) }
        }
    }

    // 从列表搜索进入：滚动定位到第一条命中条文（点击进入该条全文）
    val firstHitIndex = remember(book.id, rows, highlight) {
        if (highlight.isBlank()) -1
        else rows.indexOfFirst { it is LawRow.Article && matches(it.article, highlight) }
    }
    LaunchedEffect(book.id, firstHitIndex) {
        if (firstHitIndex >= 0) listState.scrollToItem(firstHitIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MiuiTopAppBar(
            title = {
                Text(
                    book.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        // 页内搜索框（搜索本书全部条文）
        MiuiTextField(
            value = innerQuery,
            onValueChange = { innerQuery = it },
            placeholder = "搜索法条全文/标题（如：受案登记、鉴定）",
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                MiuiCard {
                    Text(
                        "共${book.articlesCount}条·${book.chapters}章",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        book.intro,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (innerQuery.isNotBlank()) {
                item {
                    Text(
                        "共命中 ${filteredArticles.size} 条相关条文",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (hasChapters) {
                // 按章分组：章节头 + 条号/标题行（点击进入该条条文全文）
                items(
                    rows,
                    key = { row ->
                        when (row) {
                            is LawRow.Chapter -> "ch:${row.name}"
                            is LawRow.Article -> "ar:${row.article.no}"
                        }
                    }
                ) { row ->
                    when (row) {
                        is LawRow.Chapter -> ChapterHeader(row.name, row.count)
                        is LawRow.Article -> ArticleRow(
                            article = row.article,
                            highlight = if (innerQuery.isNotBlank()) innerQuery else highlight,
                            onClick = { selectedArticle = row.article }
                        )
                    }
                }
            } else {
                items(rows, key = { (it as LawRow.Article).article.no }) { row ->
                    ArticleRow(
                        article = (row as LawRow.Article).article,
                        highlight = if (innerQuery.isNotBlank()) innerQuery else highlight,
                        onClick = { selectedArticle = row.article }
                    )
                }
            }
        }
    }
}

/** 章节标题行 */
@Composable
private fun ChapterHeader(name: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "${count}条",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 条文行：只显示条号/标题（不显示条文全文），点击进入该条条文全文 */
@Composable
private fun ArticleRow(
    article: LawArticle,
    highlight: String,
    onClick: () -> Unit
) {
    MiuiCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = highlightText(article.no, highlight),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (article.title.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = highlightText(article.title, highlight),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 第二级：选中条文全文页（显示该条完整法条，关键词字体高亮） */
@Composable
private fun ArticleDetailScreen(
    book: LawBook,
    article: LawArticle,
    highlight: String,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    Column(modifier = Modifier.fillMaxSize()) {
        MiuiTopAppBar(
            title = {
                Text(
                    "${book.name} · ${article.no}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            item {
                MiuiCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = highlightText(article.no, highlight),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (article.title.isNotBlank()) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = highlightText(article.title, highlight),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = highlightText(article.content, highlight),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    )
                }
            }
        }
    }
}

/** 把 text 中所有 query 出现处高亮（v1.66）：字体浅黄色加粗（无底色），与普通正文明显区别 */
@Composable
private fun highlightText(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val hlColor = com.casetracker.theme.HighlightYellowText
    return buildAnnotatedString {
        var index = 0
        val lower = text.lowercase()
        val q = query.lowercase()
        while (index <= text.length) {
            val found = lower.indexOf(q, index)
            if (found < 0) {
                append(text.substring(index))
                break
            }
            append(text.substring(index, found))
            withStyle(SpanStyle(color = hlColor, fontWeight = FontWeight.Bold)) {
                append(text.substring(found, found + query.length))
            }
            index = found + query.length
        }
    }
}

/** v1.73 搜索结果：命中法规（名称命中）+ 命中条文 */
private data class SearchResult(
    val hits: List<HitArticle>,
    val bookHits: List<BookHit>,
    val highlight: String
)

/** 命中法规（approx=true 表示名称错字/漏字近似命中） */
private data class BookHit(
    val law: LawBook,
    val approx: Boolean
)

/** 条文匹配结果 */
private enum class MatchKind { EXACT, APPROX, NONE }

/** v1.73 条文引用：由查询串解析出的（条号, 款号, 剩余名称/关键词） */
private data class ArticleRef(
    val articleNo: Int,
    val clause: Int,
    val nameQuery: String
)

/** 条号正则：第? X条 [第? Y] [款|项]，X/Y 支持中文数字与阿拉伯数字（如：第二十二条、十九条一、22条1款） */
private val ARTICLE_NO_REGEX = Regex(
    """第?\s*([一二三四五六七八九十百零两]+|[0-9０-９]+)\s*条\s*第?\s*([一二三四五六七八九十]+|[0-9]+)?\s*(?:款|项)?"""
)

/** 从查询串解析条文引用；无法解析出条号时返回 null（走全文/标题关键词匹配） */
private fun parseArticleRef(q: String): ArticleRef? {
    val m = ARTICLE_NO_REGEX.find(q) ?: return null
    val rawNo = m.groupValues[1]
    val articleNo = rawNo.toIntOrNull() ?: cnToInt(rawNo)
    val rawClause = m.groupValues[2]
    val clause = if (rawClause.isBlank()) 0 else (rawClause.toIntOrNull() ?: cnToInt(rawClause))
    val nameQuery = q.removeRange(m.range).trim()
    return ArticleRef(articleNo, clause, nameQuery)
}

/** 从条文编号（如"第二十二条""第四十二条第一款"）提取条号；非条文式编号（要点一/适用指引）返回 null */
private fun articleNoOf(no: String): Int? {
    val m = Regex("""第\s*([一二三四五六七八九十百零两]+)\s*条""").find(no) ?: return null
    return cnToInt(m.groupValues[1])
}

/**
 * 条文命中判定（v1.87 模糊搜索版）：
 * - 有条号引用时按「条号 + 名称/关键词」匹配（关键词同样支持拆词与错字容错）；
 * - 无条号时按全文/标题/条号关键词匹配：所有拆出的关键词都必须命中（AND），
 *   任一关键词为近似命中则整条标记 APPROX。
 */
private fun matchArticle(
    law: LawBook,
    a: LawArticle,
    terms: List<String>,
    ref: ArticleRef?
): MatchKind {
    if (ref != null) {
        val an = articleNoOf(a.no) ?: return MatchKind.NONE
        if (an != ref.articleNo) return MatchKind.NONE
        if (terms.isEmpty()) return MatchKind.EXACT
        var approx = false
        for (t in terms) {
            val haystack = law.name + "\n" + a.content + "\n" + a.title
            val exact = haystack.contains(t, ignoreCase = true)
            val ap = !exact && fuzzyContains(haystack, t)
            if (!exact && !ap) return MatchKind.NONE
            if (!exact) approx = true
        }
        return if (approx) MatchKind.APPROX else MatchKind.EXACT
    }
    if (terms.isEmpty()) return MatchKind.NONE
    var approx = false
    for (t in terms) {
        val haystack = a.content + "\n" + a.title + "\n" + a.no
        val exact = haystack.contains(t, ignoreCase = true)
        val ap = !exact && fuzzyContains(haystack, t)
        if (!exact && !ap) return MatchKind.NONE
        if (!exact) approx = true
    }
    return if (approx) MatchKind.APPROX else MatchKind.EXACT
}

/** 拆词：按空白/逗号/顿号拆成多个关键词，去空、去重 */
private fun splitTerms(q: String): List<String> =
    q.split(Regex("[\\s,，、;；]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

/**
 * 模糊子串匹配：先精确子串 contains；未命中且词长 >= 2 时启用错字/漏字容错
 * （编辑距离：2~4 字词允许错 1 字、5 字以上允许错 2 字）。
 * 容错仅在首字符出现的位置附近扫描，控制全文扫描开销。
 */
private fun fuzzyContains(text: String, term: String): Boolean {
    if (term.isBlank() || term.length < 2) return false
    if (text.contains(term, ignoreCase = true)) return true
    val maxDist = if (term.length >= 5) 2 else 1
    val first = term[0]
    val minWin = term.length - maxDist
    val maxWin = term.length + maxDist
    var idx = text.indexOf(first)
    while (idx != -1) {
        for (winLen in minWin..maxWin) {
            if (idx + winLen > text.length) break
            if (levenshtein(text.substring(idx, idx + winLen), term) <= maxDist) return true
        }
        idx = text.indexOf(first, idx + 1)
    }
    return false
}

/** Levenshtein 编辑距离（小窗口比对，开销可控） */
private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = dp[0]
        dp[0] = i
        for (j in 1..b.length) {
            val tmp = dp[j]
            dp[j] = minOf(
                dp[j] + 1,
                dp[j - 1] + 1,
                prev + if (a[i - 1] == b[j - 1]) 0 else 1
            )
            prev = tmp
        }
    }
    return dp[b.length]
}

/** 中文数字转阿拉伯数字（支持 十/百/千/零，如：二十二→22、十九→19、一百零五→105、两→2） */
private fun cnToInt(s: String): Int {
    if (s.isBlank()) return 0
    val digits = mapOf(
        '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9, '零' to 0
    )
    var result = 0
    var num = 0
    for (c in s) {
        when (c) {
            '十' -> { result += (num.takeIf { it > 0 } ?: 1) * 10; num = 0 }
            '百' -> { result += (num.takeIf { it > 0 } ?: 1) * 100; num = 0 }
            '千' -> { result += (num.takeIf { it > 0 } ?: 1) * 1000; num = 0 }
            else -> digits[c]?.let { num = it }
        }
    }
    return result + num
}
