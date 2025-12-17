package org.bigboyapps.rngenerator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bigboyapps.rngenerator.logging.AgentLogEntry

// Terminal colors
val TerminalBackground = Color(0xFF1E1E1E)
val TerminalText = Color(0xFFD4D4D4)
val NarrativeColor = Color(0xFFE0E0E0)
val NpcColor = Color(0xFF4FC3F7)
val CombatColor = Color(0xFFFFB74D)
val SystemColor = Color(0xFF81C784)
val UserInputColor = Color(0xFF64B5F6)
val ErrorColor = Color(0xFFE57373)
val ActionColor = Color(0xFFA5D6A7)
val LogQueryColor = Color(0xFFCE93D8)
val LogResponseColor = Color(0xFF80DEEA)

@Composable
fun TerminalScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val agentLogs by viewModel.agentLogs.collectAsState()
    var showLogs by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBackground)
    ) {
        // Header with toggle
        TerminalHeader(
            isGameActive = uiState.isGameActive,
            playerName = uiState.playerName,
            playerLevel = uiState.playerLevel,
            showLogs = showLogs,
            onToggleLogs = { showLogs = !showLogs }
        )

        // Main content area
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Terminal output
            TerminalOutput(
                lines = uiState.terminalLines,
                isLoading = uiState.isLoading,
                modifier = Modifier.weight(if (showLogs) 0.6f else 1f)
            )

            // Agent logs panel (collapsible)
            if (showLogs) {
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    thickness = 1.dp,
                    color = Color(0xFF404040)
                )
                AgentLogsPanel(
                    logs = agentLogs,
                    modifier = Modifier.weight(0.4f)
                )
            }
        }

        // Action buttons (if available)
        if (uiState.actionOptions.isNotEmpty()) {
            ActionButtons(
                actions = uiState.actionOptions,
                onActionSelected = viewModel::onActionSelected
            )
        }

        // Input field
        TerminalInput(
            value = uiState.currentInput,
            onValueChange = viewModel::onInputChanged,
            onSubmit = viewModel::onSubmitInput,
            isLoading = uiState.isLoading
        )
    }
}

@Composable
private fun TerminalHeader(
    isGameActive: Boolean,
    playerName: String,
    playerLevel: Int,
    showLogs: Boolean,
    onToggleLogs: () -> Unit
) {
    Surface(
        color = Color(0xFF2D2D2D),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isGameActive) "⚔ $playerName (Lv.$playerLevel)" else "RPGenerator",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SystemColor
                )
            )

            TextButton(onClick = onToggleLogs) {
                Text(
                    text = if (showLogs) "Hide Logs" else "Show Logs",
                    color = LogQueryColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun TerminalOutput(
    lines: List<TerminalLine>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new lines are added
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(lines) { line ->
                TerminalLineText(line)
            }

            if (isLoading) {
                item {
                    Text(
                        text = "▌",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = SystemColor
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalLineText(line: TerminalLine) {
    val color = when (line.type) {
        TerminalLine.LineType.NARRATIVE -> NarrativeColor
        TerminalLine.LineType.NPC_DIALOGUE -> NpcColor
        TerminalLine.LineType.COMBAT -> CombatColor
        TerminalLine.LineType.SYSTEM -> SystemColor
        TerminalLine.LineType.USER_INPUT -> UserInputColor
        TerminalLine.LineType.ERROR -> ErrorColor
        TerminalLine.LineType.ACTION_OPTION -> ActionColor
    }

    Text(
        text = line.text,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = color,
            lineHeight = 20.sp
        )
    )
}

@Composable
private fun ActionButtons(
    actions: List<String>,
    onActionSelected: (Int) -> Unit
) {
    Surface(
        color = Color(0xFF252525),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Quick Actions:",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actions.take(4).forEachIndexed { index, action ->
                    OutlinedButton(
                        onClick = { onActionSelected(index) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ActionColor
                        )
                    ) {
                        Text(
                            text = "${index + 1}. ${action.take(15)}${if (action.length > 15) "..." else ""}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        color = Color(0xFF2D2D2D),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "> ",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    color = UserInputColor
                )
            )
            TextField(
                value = value,
                onValueChange = onValueChange,
                enabled = !isLoading,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = TerminalText
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = TerminalText
                ),
                placeholder = {
                    Text(
                        text = if (isLoading) "Processing..." else "Type a command...",
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF666666)
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                singleLine = true
            )
            IconButton(
                onClick = onSubmit,
                enabled = !isLoading && value.isNotEmpty()
            ) {
                Text(
                    text = "→",
                    style = TextStyle(
                        fontSize = 20.sp,
                        color = if (!isLoading && value.isNotEmpty()) UserInputColor else Color(0xFF666666)
                    )
                )
            }
        }
    }
}

@Composable
private fun AgentLogsPanel(
    logs: List<AgentLogEntry>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        // Header
        Surface(
            color = Color(0xFF252525),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Agent Logs (${logs.size})",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = LogQueryColor
                ),
                modifier = Modifier.padding(8.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs) { entry ->
                AgentLogEntryCard(entry)
            }
        }
    }
}

@Composable
private fun AgentLogEntryCard(entry: AgentLogEntry) {
    val isQuery = entry.direction == AgentLogEntry.Direction.QUERY
    val bgColor = if (isQuery) Color(0xFF2A1F2A) else Color(0xFF1F2A2A)
    val textColor = if (isQuery) LogQueryColor else LogResponseColor
    val label = if (isQuery) "→ QUERY" else "← RESPONSE"

    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${entry.agentName} $label",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                )
                Text(
                    text = entry.formattedTime,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF666666)
                    )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.content.take(500) + if (entry.content.length > 500) "..." else "",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFAAAAAA),
                    lineHeight = 14.sp
                )
            )
        }
    }
}
