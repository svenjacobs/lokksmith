package dev.lokksmith.client.request.flow

import dev.lokksmith.client.Client
import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.snapshot.Snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Provides an observable [Result] of the current or last known progress of an auth flow which
 * can be used to display some progression in the UI layer of an application.
 *
 * @see AuthFlowResultProvider.forClient
 * @see AuthFlowResultProvider.confirmConsumed
 * @see authFlowResult
 */
public object AuthFlowResultProvider {

    public sealed interface Result {

        public data object Undefined : Result

        public data object Processing : Result

        public data object Success : Result

        public data object Cancelled : Result

        public data class Error(
            val code: String?,
            val message: String?,
        ) : Result
    }

    /**
     * Provides an observable [Result] of the current or last known progress of an auth flow which
     * can be used to display some progression in the UI layer of an application. Returns
     * [Result.Undefined] if no auth flow is currently being processed.
     *
     * @see authFlowResult
     */
    public fun forClient(client: Client): Flow<Result> =
        (client as InternalClient).snapshots
            .map { snapshot ->
                val flowResult = snapshot.flowResult
                when {
                    flowResult == Snapshot.FlowResult.Success -> Result.Success
                    flowResult == Snapshot.FlowResult.Cancelled -> Result.Cancelled
                    flowResult is Snapshot.FlowResult.Error -> Result.Error(
                        code = flowResult.code,
                        message = flowResult.message,
                    )

                    snapshot.ephemeralFlowState != null -> Result.Processing
                    else -> Result.Undefined
                }
            }
            .distinctUntilChanged()

    /**
     * Confirms that the result has been consumed and displayed to the user.
     * Resets the internal result state so that it does not reappear.
     *
     * @see confirmAuthFlowResultConsumed
     */
    public suspend fun confirmConsumed(client: Client) {
        (client as InternalClient).updateSnapshot {
            copy(
                flowResult = null,
            )
        }
    }
}

/**
 * @see AuthFlowResultProvider.forClient
 */
public val Client.authFlowResult: Flow<AuthFlowResultProvider.Result>
    get() = AuthFlowResultProvider.forClient(this)

/**
 * @see AuthFlowResultProvider.confirmConsumed
 */
public suspend fun Client.confirmAuthFlowResultConsumed() {
    AuthFlowResultProvider.confirmConsumed(this)
}
