package com.casetracker.ui.law

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.casetracker.ui.components.MiuiCard
import com.casetracker.ui.components.MiuiTextField
import com.casetracker.ui.components.MiuiTopAppBar

private val ALL_LAWS = LAW_BOOKS + LAW_BOOKS_EXTRA

@Composable
fun LawLibraryScreen() {
    var selectedBook by remember { mutableStateOf<LawBook?>(null) }
    val book = selectedBook
    if (book == null) {
        LawListScreen(onOpen = { selectedBook = it })
    } else {
        LawDetailScreen(book = book, onBack = { selectedBook = null })
    }
}

@Composable
private fun LawListScreen(onOpen: (LawBook) -> Unit) {
    var query by remember { mutableStateOf("") }

    // 搜索：匹配法规标题、简介或条文全文
    val result = remember(query) {
        if (query.isBlank()) ALL_LAWS.map { it to 0 }
        else ALL_LAWS.mapNotNull { law ->
            val hitCount = law.articles.count {
                it.content.contains(query, ignoreCase = true) ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.no.contains(query)
            }
            val nameHit = law.name.contains(query, ignoreCase = true) ||
                law.intro.contains(query, ignoreCase = true)
            if (hitCount > 0 || nameHit) law to hitCount else null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MiuiTopAppBar(
            title = {
                Column {
                    Text("法律法规", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "道路交通事故处理相关法规",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            MiuiTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索法条全文/标题",
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (result.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "未找到与「$query」相关的法规条文",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            result.forEach { (law, hitCount) ->
                LawCard(
                    law = law,
                    hitCount = if (query.isBlank()) 0 else hitCount,
                    onClick = { onOpen(law) }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LawCard(
    law: LawBook,
    hitCount: Int,
    onClick: () -> Unit
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
                Text(
                    law.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "共${law.articlesCount}条·${law.chapters}章",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (hitCount > 0) "命中 $hitCount 条相关条文 →" else "点击查看 →",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LawDetailScreen(
    book: LawBook,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(book, query) {
        if (query.isBlank()) book.articles
        else book.articles.filter {
            it.content.contains(query, ignoreCase = true) ||
                it.title.contains(query, ignoreCase = true) ||
                it.no.contains(query)
        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
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
            MiuiTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索本法规条文",
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
            if (query.isNotBlank() && shown.isEmpty()) {
                Text(
                    "本法规未找到与「$query」相关的条文",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            shown.forEach { article ->
                ArticleCard(article)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ArticleCard(article: LawArticle) {
    MiuiCard {
        Text(
            "${article.no}　${article.title}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            article.content,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        )
    }
}
