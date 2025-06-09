package dev.lokksmith.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.lokksmith.Lokksmith
import dev.lokksmith.client.Client
import dev.lokksmith.client.request.OAuthResponseException
import dev.lokksmith.client.request.flow.AuthFlow.Initiation
import dev.lokksmith.client.request.flow.AuthFlowResultProvider.Result
import dev.lokksmith.client.request.flow.authFlowResult
import dev.lokksmith.client.request.flow.authorization_code.AuthorizationCodeFlow
import dev.lokksmith.client.request.flow.confirmAuthFlowResultConsumed
import dev.lokksmith.client.request.flow.end_session.EndSessionFlow
import dev.lokksmith.client.request.parameter.Scope
import dev.lokksmith.client.usecase.RunWithTokensOrResetUseCase
import dev.lokksmith.demo.AppViewModel.UiState.AuthFlow
import dev.lokksmith.discoveryUrl
import dev.lokksmith.id
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val lokksmith: Lokksmith = Container.lokksmith,
) : ViewModel() {

    data class UiState(
        val isInitialized: Boolean = false,
        val isLoading: Boolean = false,
        val tokens: Client.Tokens? = null,
        val authFlow: AuthFlow? = null,
        val error: String? = null,
        val runWithTokensResponse: String? = null,
    ) {
        data class AuthFlow(
            val initiation: Initiation,
            val redirectScheme: String,
        )
    }

    private val client = MutableStateFlow<Client?>(null)
    private val authFlow = MutableStateFlow<AuthFlow?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val isLoading = MutableStateFlow(false)
    private val runWithTokensResponse = MutableStateFlow<String?>(null)

    private var job: Job? = null

    val uiState: StateFlow<UiState> = combine(
        client.map { it != null },
        isLoading,
        client.flatMapLatest { it?.tokens ?: flowOf(null) },
        authFlow,
        error,
        runWithTokensResponse,
    ) { arr ->
        UiState(
            isInitialized = arr[0] as Boolean,
            isLoading = arr[1] as Boolean,
            tokens = arr[2] as Client.Tokens?,
            authFlow = arr[3] as AuthFlow?,
            error = arr[4] as String?,
            runWithTokensResponse = arr[5] as String?,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = UiState(),
    )

    fun onStart() {
        job?.cancel()
        job = viewModelScope.launch {
            client
                .flatMapLatest { client -> client?.authFlowResult ?: flowOf(null) }
                .filterNotNull()
                .collect { result ->
                    when (result) {
                        Result.Processing -> isLoading.value = true

                        Result.Cancelled -> {
                            isLoading.value = false
                            error.value = "Flow was cancelled"
                        }

                        is Result.Error -> {
                            isLoading.value = false
                            error.value = result.message
                        }

                        Result.Success -> isLoading.value = false

                        Result.Undefined -> isLoading.value = false
                    }

                    client.value?.confirmAuthFlowResultConsumed()
                }
        }
    }

    fun onStop() {
        job?.cancel()
        job = null
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Logger.e(TAG, throwable) { "Lokksmith error" }

        if (throwable is OAuthResponseException) {
            error.value = "Error: ${throwable.error}, ${throwable.errorDescription}"
        } else {
            error.value = "Error: $throwable"
        }
    }

    fun onGetOrCreateClientClick(
        clientId: String,
        url: String,
    ) {
        viewModelScope.launch(exceptionHandler) {
            val client = lokksmith.getOrCreate(clientId) {
                id = clientId
                discoveryUrl = url
            }
            this@AppViewModel.client.value = client
        }
    }

    fun onAuthorizationCodeFlowClick(redirectUri: String) {
        val redirectUri = redirectUri.trim()
        val flow = client.value?.authorizationCodeFlow(
            AuthorizationCodeFlow.Request(
                redirectUri = redirectUri,
                scope = setOf(Scope.Email, Scope.Profile),
            )
        ) ?: return

        viewModelScope.launch(exceptionHandler) {
            authFlow.value = AuthFlow(
                initiation = flow.prepare(),
                redirectScheme = redirectUri.split(":").first(),
            )
        }
    }

    fun onEndSessionCodeFlowClick(redirectUri: String) {
        val redirectUri = redirectUri.trim()
        val flow = client.value?.endSessionFlow(
            EndSessionFlow.Request(
                redirectUri = redirectUri,
            )
        ) ?: return

        viewModelScope.launch(exceptionHandler) {
            authFlow.value = AuthFlow(
                initiation = flow.prepare(),
                redirectScheme = redirectUri.split(":").first(),
            )
        }
    }

    fun onConfirmAuthFlow() {
        authFlow.value = null
    }

    fun onRefreshTokensClick() {
        viewModelScope.launch(exceptionHandler) {
            client.value?.refresh()
        }
    }

    fun onResetTokensClick() {
        viewModelScope.launch(exceptionHandler) {
            client.value?.resetTokens()
        }
    }

    fun onRunWithTokensClick() {
        viewModelScope.launch {
            client.value?.runWithTokens { tokens ->
                runWithTokensResponse.value = "Access Token: ${tokens.accessToken.token.take(20)}…"
            }
        }
    }

    fun onRunWithTokensOrResetUseCaseClick() {
        viewModelScope.launch {
            val useCase = RunWithTokensOrResetUseCase(client.value!!)
            useCase { tokens ->
                runWithTokensResponse.value = "Access Token: ${tokens.accessToken.token.take(20)}…"
            }
        }
    }

    fun onConfirmRunWithTokensResultShown() {
        runWithTokensResponse.value = null
    }

    fun onConfirmErrorShown() {
        error.value = null
    }

    private companion object {
        private val TAG = AppViewModel::class.simpleName.orEmpty()
    }
}
