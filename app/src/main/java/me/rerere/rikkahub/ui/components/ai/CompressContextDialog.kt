package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.RabbitLoadingIndicator

@Composable
private fun CustomNumberSelector(
    label: String,
    options: List<Int>,
    selectedOption: Int,
    onOptionSelected: (Int) -> Unit,
    customText: String,
    onCustomTextChange: (String) -> Unit,
    isCustom: Boolean,
    onIsCustomChange: (Boolean) -> Unit,
    defaultCustomValue: Int,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var isEditing by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isCustom && !isEditing) {
                TextButton(
                    onClick = { isEditing = true },
                    contentPadding = PaddingValues()
                ) {
                    Text(
                        text = customText.toIntOrNull()?.toString()
                            ?: stringResource(R.string.chat_page_compress_custom_count),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (!isCustom) {
                TextButton(
                    onClick = {
                        onIsCustomChange(true)
                        isEditing = true
                    },
                    contentPadding = PaddingValues()
                ) {
                    Text(
                        text = stringResource(R.string.chat_page_compress_custom_count),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = HugeIcons.ArrowRight01,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        LaunchedEffect(isEditing) {
            if (isEditing) {
                focusRequester.requestFocus()
            }
        }

        if (isCustom && isEditing) {
            OutlinedTextField(
                value = customText,
                onValueChange = { onCustomTextChange(it.filter { c -> c.isDigit() }) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (customText.isNotBlank()) {
                            isEditing = false
                            keyboardController?.hide()
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            )
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = selectedOption == value && !isCustom,
                    onClick = {
                        onOptionSelected(value)
                        onIsCustomChange(false)
                        isEditing = false
                    },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size
                    )
                ) {
                    Text("$value")
                }
            }
        }
    }
}

@Composable
fun CompressContextDialog(
    onDismiss: () -> Unit,
    onConfirm: (additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int) -> Job
) {
    var additionalPrompt by remember { mutableStateOf("") }
    var selectedTokens by remember { mutableIntStateOf(2000) }
    var keepRecentMessages by remember { mutableIntStateOf(32) }
    var customTokenText by remember { mutableStateOf("") }
    var customKeepRecentText by remember { mutableStateOf("") }
    var isCustomTokens by remember { mutableStateOf(false) }
    var isCustomKeepRecent by remember { mutableStateOf(false) }
    val customTokenFocusRequester = remember { FocusRequester() }
    val customKeepRecentFocusRequester = remember { FocusRequester() }
    val tokenOptions = listOf(500, 1000, 2000, 4000)
    val keepRecentOptions = listOf(0, 16, 32, 64)
    var currentJob by remember { mutableStateOf<Job?>(null) }
    val isLoading = currentJob?.isActive == true

    LaunchedEffect(currentJob) {
        currentJob?.join()
        if (currentJob?.isCompleted == true && currentJob?.isCancelled == false) {
            onDismiss()
        }
        currentJob = null
    }

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) {
                onDismiss()
            }
        },
        title = {
            Text(stringResource(R.string.chat_page_compress_context_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RabbitLoadingIndicator(
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.chat_page_compressing))
                    }
                } else {
                    Text(stringResource(R.string.chat_page_compress_context_desc))

                    CustomNumberSelector(
                        label = stringResource(R.string.chat_page_compress_target_tokens),
                        options = tokenOptions,
                        selectedOption = selectedTokens,
                        onOptionSelected = { selectedTokens = it },
                        customText = customTokenText,
                        onCustomTextChange = { customTokenText = it },
                        isCustom = isCustomTokens,
                        onIsCustomChange = { isCustomTokens = it },
                        defaultCustomValue = 2000,
                        focusRequester = customTokenFocusRequester
                    )

                    CustomNumberSelector(
                        label = stringResource(R.string.chat_page_compress_keep_recent),
                        options = keepRecentOptions,
                        selectedOption = keepRecentMessages,
                        onOptionSelected = { keepRecentMessages = it },
                        customText = customKeepRecentText,
                        onCustomTextChange = { customKeepRecentText = it },
                        isCustom = isCustomKeepRecent,
                        onIsCustomChange = { isCustomKeepRecent = it },
                        defaultCustomValue = 32,
                        focusRequester = customKeepRecentFocusRequester
                    )

                    OutlinedTextField(
                        value = additionalPrompt,
                        onValueChange = { additionalPrompt = it },
                        label = {
                            Text(stringResource(R.string.chat_page_compress_additional_prompt))
                        },
                        placeholder = {
                            Text(stringResource(R.string.chat_page_compress_additional_prompt_hint))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                    )

                    Text(
                        text = stringResource(R.string.chat_page_compress_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (isLoading) {
                TextButton(onClick = {
                    currentJob?.cancel()
                    currentJob = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            } else {
                TextButton(onClick = {
                    val resolvedTokens = if (isCustomTokens) {
                        customTokenText.toIntOrNull() ?: 2000
                    } else {
                        selectedTokens
                    }
                    val resolvedKeepRecent = if (isCustomKeepRecent) {
                        customKeepRecentText.toIntOrNull() ?: 32
                    } else {
                        keepRecentMessages
                    }
                    currentJob = onConfirm(additionalPrompt, resolvedTokens, resolvedKeepRecent)
                }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        },
        dismissButton = {
            if (!isLoading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
