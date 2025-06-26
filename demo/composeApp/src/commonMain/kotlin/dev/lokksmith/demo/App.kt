@file:OptIn(ExperimentalTime::class)

package dev.lokksmith.demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.touchlab.kermit.Logger
import dev.lokksmith.client.Client
import dev.lokksmith.compose.rememberAuthFlowLauncher
import dev.lokksmith.demo.resources.Res
import dev.lokksmith.demo.resources.check
import dev.lokksmith.demo.resources.missing
import dev.lokksmith.demo.resources.question
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.offsetAt
import kotlinx.datetime.periodUntil
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun App(
    viewModel: AppViewModel = viewModel { AppViewModel() },
    onCopyToClipboard: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackBarHostState = remember { SnackbarHostState() }
    val launcher = rememberAuthFlowLauncher()

    LaunchedEffect(launcher.result) {
        Logger.d("App") { "Received auth flow result: ${launcher.result}" }
    }

    LifecycleStartEffect(Unit) {
        viewModel.onStart()
        onStopOrDispose { viewModel.onStop() }
    }

    LaunchedEffect(uiState.authFlow) {
        uiState.authFlow?.let {
            launcher.launch(it.initiation)
        }
        viewModel.onConfirmAuthFlow()
    }

    LaunchedEffect(uiState.error) {
        if (uiState.error == null) return@LaunchedEffect

        snackBarHostState.showSnackbar(
            message = uiState.error.orEmpty(),
            duration = SnackbarDuration.Long,
        )

        viewModel.onConfirmErrorShown()
    }

    AppTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("Lokksmith Demo") }
                    )
                },
                snackbarHost = {
                    SnackbarHost(snackBarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = AppColors.Red,
                            contentColor = Color.White,
                        )
                    }
                },
            ) { contentPadding ->
                Column(
                    modifier = Modifier
                        .padding(contentPadding)
                        .padding(horizontal = 16.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    ClientSection(
                        modifier = Modifier.padding(bottom = 16.dp),
                        onGetOrCreateClientClick = viewModel::onGetOrCreateClientClick,
                    )

                    FlowsSection(
                        modifier = Modifier.padding(bottom = 16.dp),
                        isInitialized = uiState.isInitialized,
                        hasTokens = uiState.tokens != null,
                        onAuthorizationCodeFlowClick = viewModel::onAuthorizationCodeFlowClick,
                        onEndSessionCodeFlowClick = viewModel::onEndSessionCodeFlowClick,
                    )

                    TokensSection(
                        tokens = uiState.tokens,
                        onRefreshTokensClick = viewModel::onRefreshTokensClick,
                        onResetTokensClick = viewModel::onResetTokensClick,
                        onRunWithTokensClick = viewModel::onRunWithTokensClick,
                        onRunWithTokensOrResetUseCaseClick = viewModel::onRunWithTokensOrResetUseCaseClick,
                        onCopyToClipboard = onCopyToClipboard,
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = 0.9f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 6.dp,
                    )
                }
            }
        }
    }

    uiState.runWithTokensResponse?.let {
        AlertDialog(
            onDismissRequest = viewModel::onConfirmRunWithTokensResultShown,
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmRunWithTokensResultShown) {
                    Text("OK")
                }
            },
            text = { Text(it) }
        )
    }
}

@Composable
private fun ClientSection(
    onGetOrCreateClientClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var clientId by remember { mutableStateOf("my-client-id") }
    var discoveryUrl by remember { mutableStateOf("https://example.com/.well-known/openid-configuration") }

    Column(modifier = modifier) {
        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = "Client",
            style = MaterialTheme.typography.headlineLarge,
        )

        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = "ID",
            style = MaterialTheme.typography.titleMedium,
        )

        TextField(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
            value = clientId,
            onValueChange = { clientId = it }
        )

        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = "Discovery URL",
            style = MaterialTheme.typography.titleMedium,
        )

        TextField(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .fillMaxWidth(),
            value = discoveryUrl,
            onValueChange = { discoveryUrl = it }
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = clientId.isNotEmpty() && discoveryUrl.isNotEmpty(),
            onClick = { onGetOrCreateClientClick(clientId, discoveryUrl) }
        ) {
            Text("Get or create client")
        }
    }
}

@Composable
private fun FlowsSection(
    isInitialized: Boolean,
    hasTokens: Boolean,
    onAuthorizationCodeFlowClick: (String) -> Unit,
    onEndSessionCodeFlowClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var codeFlowRedirectUri by remember { mutableStateOf("my-app://openid-response") }
    var endSessionRedirectUri by remember { mutableStateOf("my-app://openid-response") }
    val reminderText = remember {
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append("Reminder:")
            }
            append(" Did you adjust ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append("lokksmithRedirectScheme")
            }
            append(" in build.gradle.kts?")
        }
    }

    Column(modifier = modifier) {
        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = "Flows",
            style = MaterialTheme.typography.headlineLarge,
        )

        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = "Authorization Code Flow",
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = "Redirect URI",
            style = MaterialTheme.typography.titleMedium,
        )

        TextField(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .fillMaxWidth(),
            value = codeFlowRedirectUri,
            onValueChange = { codeFlowRedirectUri = it }
        )

        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = reminderText,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.Yellow,
        )

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            enabled = isInitialized,
            onClick = { onAuthorizationCodeFlowClick(codeFlowRedirectUri) },
        ) {
            Text(
                text = "Authorization Code Flow",
                textAlign = TextAlign.Center,
            )
        }

        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = "End Session Flow",
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = "Redirect URI",
            style = MaterialTheme.typography.titleMedium,
        )

        TextField(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .fillMaxWidth(),
            value = endSessionRedirectUri,
            onValueChange = { endSessionRedirectUri = it }
        )

        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = reminderText,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.Yellow,
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = hasTokens,
            onClick = { onEndSessionCodeFlowClick(endSessionRedirectUri) },
        ) {
            Text(
                text = "End Session Flow",
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TokensSection(
    tokens: Client.Tokens?,
    onRefreshTokensClick: () -> Unit,
    onResetTokensClick: () -> Unit,
    onRunWithTokensClick: () -> Unit,
    onRunWithTokensOrResetUseCaseClick: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = "Tokens",
            style = MaterialTheme.typography.headlineLarge,
        )

        Row(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                enabled = tokens != null,
                onClick = onRefreshTokensClick,
            ) {
                Text("Refresh tokens")
            }

            Button(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                enabled = tokens != null,
                onClick = onResetTokensClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Red,
                ),
            ) {
                Text("RESET TOKENS")
            }
        }

        Button(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .fillMaxWidth(),
            onClick = onRunWithTokensClick,
            enabled = tokens != null,
        ) {
            Text("runWithTokens()")
        }

        Button(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
            onClick = onRunWithTokensOrResetUseCaseClick,
            enabled = tokens != null,
        ) {
            Text("RunWithTokensOrResetUseCase")
        }

        TokenInfo(
            modifier = Modifier.padding(bottom = 16.dp),
            name = "Access Token",
            token = tokens?.accessToken?.token,
            isInitialized = tokens != null,
            expiration = tokens?.accessToken?.expiresAt,
            onCopyToClipboard = onCopyToClipboard,
        )

        TokenInfo(
            modifier = Modifier.padding(bottom = 16.dp),
            name = "Refresh Token",
            token = tokens?.refreshToken?.token,
            isInitialized = tokens != null,
            expiration = tokens?.refreshToken?.expiresAt,
            onCopyToClipboard = onCopyToClipboard,
        )

        TokenInfo(
            modifier = Modifier.padding(bottom = 16.dp),
            name = "ID Token",
            token = tokens?.idToken?.raw,
            isInitialized = tokens != null,
            expiration = tokens?.idToken?.expiration,
            onCopyToClipboard = onCopyToClipboard,
        )
    }
}

@Composable
private fun TokenInfo(
    name: String,
    isInitialized: Boolean,
    token: String?,
    expiration: Long?,
    onCopyToClipboard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expiresAt = remember(expiration) {
        expiration?.let {
            val instant = Instant.fromEpochSeconds(it)
            instant.format(
                DateTimeComponents.Formats.RFC_1123,
                TimeZone.currentSystemDefault().offsetAt(instant),
            )
        } ?: "???"
    }

    val expiresIn by produceState<Pair<Long?, String>>(null to "???", expiration) {
        if (expiration == null) {
            value = null to "???"
            return@produceState
        }

        val instant = Instant.fromEpochSeconds(expiration)

        while (true) {
            val period = Clock.System.now().periodUntil(instant, TimeZone.UTC)
            val seconds = expiration - Clock.System.now().epochSeconds
            val string = StringBuilder().apply {
                if (period.years != 0) {
                    append("${period.years} Year(s) ")
                }

                if (period.months != 0) {
                    append("${period.months} Month(s) ")
                }

                if (period.days != 0) {
                    append("${period.days} Day(s) ")
                }

                if (period.hours != 0) {
                    append("${period.hours} Hour(s) ")
                }

                append("${period.minutes} Minutes(s) ")
                append("${period.seconds} Second(s)")
            }.toString()

            value = seconds to string

            delay(1.seconds)
        }
    }

    Column(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val expiresInSeconds = expiresIn.first
            val (image, color) = when {
                expiresInSeconds != null && expiresInSeconds < 0 -> painterResource(Res.drawable.missing) to AppColors.Red
                expiresInSeconds != null && expiresInSeconds < 60 -> painterResource(Res.drawable.check) to AppColors.Yellow
                token != null -> painterResource(Res.drawable.check) to AppColors.Green
                isInitialized -> painterResource(Res.drawable.missing) to AppColors.Red
                else -> painterResource(Res.drawable.question) to AppColors.Yellow
            }

            Image(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(24.dp),
                painter = image,
                contentDescription = null,
                colorFilter = ColorFilter.tint(color),
            )

            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(
                modifier = Modifier.weight(1f),
            )

            TextButton(
                onClick = { onCopyToClipboard(token!!) },
                enabled = token != null,
            ) {
                Text("Copy")
            }
        }

        Text(
            text = "Expires at",
            style = MaterialTheme.typography.titleSmall,
        )

        Text(
            text = expiresAt,
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = "Expires in",
            style = MaterialTheme.typography.titleSmall,
        )

        Text(
            text = expiresIn.second,
            style = MaterialTheme.typography.bodyMedium,
            color = expiresIn.first.let {
                when {
                    it == null -> Color.Unspecified
                    it < 0 -> AppColors.Red
                    it < 60 -> AppColors.Yellow
                    else -> Color.Unspecified
                }
            }
        )
    }
}
