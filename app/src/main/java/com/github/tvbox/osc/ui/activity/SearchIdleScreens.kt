@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.github.tvbox.osc.ui.activity

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.net.SearchSettings
import com.github.tvbox.osc.ui.components.SearchActionButton
import com.github.tvbox.osc.ui.components.SettingsIconBadge
import com.github.tvbox.osc.ui.components.clearFocusOnDrag
import com.github.tvbox.osc.ui.page.ManageActionIcon
import com.github.tvbox.osc.ui.theme.cardContainer
import com.github.tvbox.osc.util.HistoryHelper

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SearchIdleContent(
    history: List<String>,
    hotSearch: List<String>,
    suggest: List<String>,
    query: String,
    topPad: Dp,
    onSearch: (String) -> Unit,
    onClearHistory: () -> Unit,
    onRemoveHistory: (String) -> Unit,
) {
    val incognito = HistoryHelper.isIncognito()
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .clearFocusOnDrag(focusManager),
    ) {
        Spacer(Modifier.height(topPad - 20.dp))
        SearchScopeActions(query = query, onSearch = onSearch)
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 32.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.cardContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsIconBadge(R.drawable.ic_search_history, stringResource(R.string.search_history))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.search_history),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                ManageActionIcon(
                    iconRes = R.drawable.ic_delete,
                    contentDescription = stringResource(R.string.search_history_clear),
                    onClick = onClearHistory,
                )
            }
            if (incognito) {
                Text(
                    text = stringResource(R.string.search_incognito),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            } else {
                AnimatedContent(
                    targetState = history,
                    contentKey = { it.isEmpty() },
                    transitionSpec = {
                        (
                            fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                            ).using(SizeTransform(clip = false))
                    },
                    label = "searchHistory",
                ) { list ->
                    if (list.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            list.forEach { word ->
                                HistoryChip(
                                    word = word,
                                    onClick = { onSearch(word) },
                                    onLongClick = { onRemoveHistory(word) },
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.cardContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val suggestTitle = if (query.isBlank()) {
                stringResource(R.string.search_hot_rank)
            } else {
                stringResource(R.string.search_suggest)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsIconBadge(R.drawable.ic_hot_search, suggestTitle)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = suggestTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            AnimatedContent(
                targetState = suggest,
                contentKey = { it.isEmpty() },
                transitionSpec = {
                    (
                        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        ).using(SizeTransform(clip = false))
                },
                label = "searchSuggest",
            ) { list ->
                if (list.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        list.forEach { word ->
                            HistoryChip(word = word, onClick = { onSearch(word) }, onLongClick = {})
                        }
                    }
                } else if (hotSearch.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hot_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                } else {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        hotSearch.chunked(2).forEachIndexed { rowIdx, pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                pair.forEachIndexed { colIdx, title ->
                                    val rank = rowIdx * 2 + colIdx + 1
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onSearch(title) }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = rank.toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (rank <= 3) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.width(20.dp),
                                        )
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = 10.dp),
                                        )
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 搜索范围三按钮，放在搜索历史上方。
 *
 * 全站 = 所有可搜索站点；站内 = 当前首页站点；选中 = 搜索设置里勾选的站点（为空时退回全站）。
 * 点了先落范围，如果搜索框已经有词就直接搜，没词就只提示已切换（没有词也没法搜）。
 */
@Composable
private fun SearchScopeActions(query: String, onSearch: (String) -> Unit) {
    val context = LocalContext.current
    val allKeys = remember {
        ApiConfig.get().getSourceBeanList().filter(SourceBean::isSearchable).map { it.key.orEmpty() }.toSet()
    }
    fun apply(range: Set<String>, hintRes: Int) {
        SearchSettings.putSourcesForSearch(range)
        SearchViewModel.loadCheckedSources()
        val text = query.trim()
        if (text.isEmpty()) {
            Toast.makeText(context, context.getString(hintRes), Toast.LENGTH_SHORT).show()
        } else {
            onSearch(text)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchActionButton(
            text = stringResource(R.string.search_action_all),
            modifier = Modifier.weight(1f),
            onClick = { apply(allKeys, R.string.search_scope_all) },
        )
        SearchActionButton(
            text = stringResource(R.string.search_action_home),
            modifier = Modifier.weight(1f),
            onClick = {
                val homeKey = ApiConfig.get().getHomeSourceBean()?.key
                val range = if (homeKey.isNullOrEmpty()) allKeys else setOf(homeKey)
                apply(range, R.string.search_scope_home)
            },
        )
        SearchActionButton(
            text = stringResource(R.string.search_action_selected),
            modifier = Modifier.weight(1f),
            onClick = {
                val selection = SearchSettings.currentSelection()
                val range = when {
                    selection == null -> allKeys
                    selection.isEmpty() -> allKeys
                    else -> selection.filter { it in allKeys }.toSet().ifEmpty { allKeys }
                }
                apply(range, R.string.search_scope_selected)
            },
        )
    }
}
