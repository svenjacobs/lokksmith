package dev.lokksmith.client.request.flow

import androidx.compose.runtime.Immutable

/**
 * Represents an authentication or authorization flow, such as the OpenID Connect Authorization Code
 * Flow.
 *
 * Implementations of this interface encapsulate the logic required to initiate the flow (by
 * constructing the appropriate request URL), handle the response after user authentication, and
 * manage cancellation.
 *
 * Typical usage:
 * 1. Call [prepare] to obtain the URL to which the user should be redirected (e.g., open in a
 *    browser).
 * 2. After the user completes authentication and your app receives the redirect URI, call
 *    [onResponse] with the full redirect URI to complete the flow and update the client state.
 * 3. If the flow is cancelled (e.g., the user aborts the process), call [cancel] to clean up
 *    resources.
 *
 * [onResponse] and [cancel] must each be called at most once per flow instance.
 */
public interface AuthFlow {

    /**
     * Common request attributes required by all flow kinds.
     */
    public interface Request {

        /**
         * Redirect URI to which the response is sent by the OpenID provider.
         */
        public val redirectUri: String

        /**
         * Length of the cryptographically random `state` parameter included in the flow request.
         *
         * The `state` parameter is required to prevent cross-site request forgery (CSRF) attacks
         * and to allow Lokksmith to restore the flow when handling the response. It is returned by
         * the OpenID provider and must be validated by the client.
         *
         * Disabling the `state` parameter is not supported, as it is essential for both security
         * and Lokksmith's flow management. While the OpenID Connect specification does not define a
         * minimum or maximum length, Lokksmith enforces a minimum of 16 characters for security.
         */
        public val stateLength: Int
    }

    /**
     * Contains the data required to initiate an auth flow.
     *
     * This class is designed to be easily serializable and should only contain basic data types,
     * making it suitable for use with UI frameworks, serialization and inter-process communication.
     */
    @Immutable
    public class Initiation(
        public val requestUrl: String,
        public val clientKey: String,
    )

    /**
     * Prepares the flow and returns an [Initiation] result which contains the request URL to
     * initiate the flow by opening it in a browser (e.g., a Custom Tab) on a mobile device.
     * The returned result can be either used to manually call the URL and handle the result or to
     * pass it to client-specific helper methods that simplify executing a flow.
     *
     * @return A result containing the URL to initiate the authentication or authorization flow.
     * @throws dev.lokksmith.client.request.RequestException if the request URL cannot be constructed.
     */
    public suspend fun prepare(): Initiation

    /**
     * Handles the redirect URI received from the OpenID provider after user authentication.
     *
     * Call this method exactly once after the user completes authentication and the app receives
     * the full redirect URI (including query parameters). Calling [onResponse] multiple times is
     * an error and might invalidate the whole flow.
     *
     * When this method returns without throwing an exception, the flow has completed successfully
     * and the [dev.lokksmith.client.Client] state has been updated.
     *
     * @param redirectUri The complete redirect URI returned by the OpenID provider.
     *
     * @throws dev.lokksmith.client.request.ResponseException
     * @throws dev.lokksmith.client.request.OAuthResponseException
     * @throws dev.lokksmith.client.request.token.TokenValidationException
     */
    public suspend fun onResponse(redirectUri: String)

    /**
     * Cancels the flow, for example if the user returns from the browser without authenticating,
     * the browser could not be started, or another OS-specific error occurred.
     *
     * This method cleans up resources and terminates the flow. The flow instance must not be used
     * after [cancel] has been called.
     */
    public suspend fun cancel()
}
