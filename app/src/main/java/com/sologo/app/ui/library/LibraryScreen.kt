package com.sologo.app.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sologo.app.AppContainer
import com.sologo.app.data.SavedGame
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    onOpenReview: (File) -> Unit
) {
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModelFactory(container))
    val games by vm.games.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<SavedGame?>(null) }
    var exportFile by remember { mutableStateOf<File?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.import(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-go-sgf")
    ) { uri: Uri? ->
        val file = exportFile
        if (uri != null && file != null) vm.export(file, uri)
        exportFile = null
    }

    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("棋谱库") },
                actions = {
                    TextButton(onClick = { importLauncher.launch(arrayOf("application/x-go-sgf", "text/plain", "*/*")) }) {
                        Text("导入 SGF")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
            if (games.isEmpty() && !busy) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("还没有棋谱", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "对局结束后点“保存 SGF”，或从其他围棋软件导入标准 FF[4] 棋谱。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = {
                        importLauncher.launch(arrayOf("application/x-go-sgf", "text/plain", "*/*"))
                    }) { Text("导入一盘棋") }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(games, key = { it.file.absolutePath }) { game ->
                    GameCard(
                        game = game,
                        onOpen = { onOpenReview(game.file) },
                        onExport = {
                            exportFile = game.file
                            exportLauncher.launch("${game.file.nameWithoutExtension}.sgf")
                        },
                        onDelete = { pendingDelete = game }
                    )
                }
            }
        }
    }

    pendingDelete?.let { game ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除棋谱") },
            text = { Text("确定删除 ${game.file.name} 及其配套分析 JSON 吗？") },
            confirmButton = {
                Button(onClick = {
                    vm.delete(game)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun GameCard(
    game: SavedGame,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "修改于 " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(Date(game.modifiedAt)) +
                    if (game.hasAnalysis) " · 已有 AI 复盘" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen) { Text("复盘") }
                OutlinedButton(onClick = onExport) { Text("导出") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

private class LibraryViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LibraryViewModel(container) as T
    }
}
