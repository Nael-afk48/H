package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleSignInButton
import com.example.auth.attemptAutoSignIn
import com.example.data.HavenRepository
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isCheckingSession by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingSession = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingSession = false
            },
            scope = scope
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_haven_luxury_hero_1795741905633),
                contentDescription = "Minimalist architectural travertine gallery",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
            )
            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "Step 1 of 2 · Quick Access",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auth_subcopy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(26.dp))

            GoogleSignInButton(
                onAuthSuccess = {
                    errorMessage = null
                    onAuthSuccess()
                },
                onAuthError = { msg -> errorMessage = msg }
            )

            AnimatedVisibility(visible = isCheckingSession) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 14.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Checking for an existing session…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

private enum class AliasAvailability {
    IDLE,
    CHECKING,
    AVAILABLE,
    TAKEN
}

@Composable
fun AliasSetupScreen(
    suggestedName: String,
    email: String,
    isBusy: Boolean,
    onCheckAvailability: suspend (String) -> Result<Boolean>,
    onSubmitAlias: (alias: String, bio: String, onError: (String) -> Unit) -> Unit,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultSuggestion = remember(suggestedName, email) {
        val raw = suggestedName.ifBlank { email.substringBefore("@") }
            .lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(20)
        if (raw.length >= 3) raw else ""
    }

    var aliasInput by remember { mutableStateOf(defaultSuggestion) }
    var bioInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var availability by remember { mutableStateOf(AliasAvailability.IDLE) }

    val normalized = aliasInput.trim().lowercase()
    val isFormatValid = HavenRepository.isValidAliasFormat(normalized)

    LaunchedEffect(normalized) {
        if (!isFormatValid) {
            availability = AliasAvailability.IDLE
            return@LaunchedEffect
        }
        availability = AliasAvailability.CHECKING
        delay(350)
        onCheckAvailability(normalized)
            .onSuccess { isFree ->
                availability = if (isFree) AliasAvailability.AVAILABLE else AliasAvailability.TAKEN
            }
            .onFailure {
                availability = AliasAvailability.IDLE
            }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Step 2 of 2 · Your Handle",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.alias_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.alias_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { next ->
                        val cleaned = next.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
                        if (cleaned.length <= 20) {
                            aliasInput = cleaned
                            errorMessage = null
                        }
                    },
                    prefix = { Text("@") },
                    label = { Text("Username") },
                    placeholder = { Text(stringResource(R.string.alias_hint)) },
                    singleLine = true,
                    trailingIcon = {
                        when (availability) {
                            AliasAvailability.CHECKING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            AliasAvailability.AVAILABLE -> {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Available",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            AliasAvailability.TAKEN -> {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = "Taken",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            AliasAvailability.IDLE -> Unit
                        }
                    },
                    supportingText = {
                        when (availability) {
                            AliasAvailability.AVAILABLE -> Text(
                                text = "@$normalized is available",
                                color = MaterialTheme.colorScheme.primary
                            )
                            AliasAvailability.TAKEN -> Text(
                                text = "@$normalized is already taken",
                                color = MaterialTheme.colorScheme.error
                            )
                            else -> Text(stringResource(R.string.alias_rules))
                        }
                    },
                    isError = errorMessage != null || availability == AliasAvailability.TAKEN,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alias_input")
                )

                OutlinedTextField(
                    value = bioInput,
                    onValueChange = { if (it.length <= 120) bioInput = it.replace("\n", " ") },
                    label = { Text("One-line bio (optional)") },
                    placeholder = { Text("e.g. Architecture, espresso & quiet nights") },
                    singleLine = true,
                    supportingText = { Text("${bioInput.length}/120 characters") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alias_bio_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        onSubmitAlias(normalized, bioInput.trim()) { err ->
                            errorMessage = err
                        }
                    },
                    enabled = isFormatValid && availability != AliasAvailability.TAKEN && !isBusy,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("alias_submit_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.alias_continue),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = email.ifBlank { "Google account" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = onSwitchAccount,
                        modifier = Modifier.testTag("switch_account_button")
                    ) {
                        Text("Switch account")
                    }
                }
            }
        }
    }
}
