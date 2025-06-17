package dev.lokksmith.client.request.flow

/**
 * Performs necessary cleanup when an auth flow was cancelled.
 */
public interface AuthFlowCancellation {

    public suspend fun cancel(state: String)
}
