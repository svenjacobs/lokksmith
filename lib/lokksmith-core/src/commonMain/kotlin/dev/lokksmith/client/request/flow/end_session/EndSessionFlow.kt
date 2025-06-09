package dev.lokksmith.client.request.flow.end_session

import dev.lokksmith.client.InternalClient
import dev.lokksmith.client.request.Defaults
import dev.lokksmith.client.request.Random
import dev.lokksmith.client.request.RequestException
import dev.lokksmith.client.request.flow.AbstractAuthFlow
import dev.lokksmith.client.request.flow.AuthFlow
import dev.lokksmith.client.request.parameter.Parameter
import dev.lokksmith.client.snapshot.Snapshot
import io.ktor.http.URLParserException
import io.ktor.http.buildUrl
import io.ktor.http.takeFrom

/**
 * Implements the OpenID Connect RP-Initiated Logout flow.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">OpenID Connect RP-Initiated Logout 1.0</a>
 */
public class EndSessionFlow internal constructor(
    client: InternalClient,
    private val request: Request,
    internal val state: String,
    private val endpoint: String,
) : AbstractAuthFlow(
    client = client,
    responseHandler = EndSessionFlowResponseHandler(state, client),
    cancellation = EndSessionFlowCancellation(client),
) {

    public data class Request(
        /**
         * Redirect URI to which the response is sent by the OpenID provider.
         */
        override val redirectUri: String,

        /**
         * Length of the cryptographically random `state` parameter included in the end session
         * request.
         *
         * The `state` parameter is required to prevent cross-site request forgery (CSRF) attacks and to
         * allow Lokksmith to restore the flow when handling the end session response. It is returned
         * by the OpenID provider and must be validated by the client.
         *
         * Disabling the `state` parameter is not supported, as it is essential for both security and
         * Lokksmith's flow management. While the OpenID Connect specification does not define a minimum
         * or maximum length, Lokksmith enforces a minimum of 16 characters for security.
         */
        override val stateLength: Int = Defaults.STATE_MIN_LENGTH,
    ) : AuthFlow.Request

    override val ephemeralFlowState: Snapshot.EphemeralFlowState
        get() = Snapshot.EphemeralEndSessionFlowState(
            state = state,
            responseUri = null,
        )

    override suspend fun onPrepare(): String = try {
        buildUrl {
            takeFrom(endpoint)

            parameters[Parameter.STATE] = state
            parameters[Parameter.POST_LOGOUT_REDIRECT_URI] = request.redirectUri
            parameters[Parameter.CLIENT_ID] = client.id.value
            client.snapshots.value.tokens?.idToken?.raw?.let { idToken ->
                parameters[Parameter.ID_TOKEN_HINT] = idToken
            }
        }.toString()
    } catch (e: URLParserException) {
        throw RequestException(
            cause = e,
            reason = RequestException.Reason.UrlParsing,
        )
    } catch (e: Exception) {
        throw RequestException(e)
    }

    internal companion object {

        internal fun createOrNull(
            client: InternalClient,
            request: Request,
            random: Random = Random(),
        ): EndSessionFlow? = client.metadata.endSessionEndpoint?.let {
            val state = when {
                request.stateLength >= Defaults.STATE_MIN_LENGTH -> random.randomAsciiString(request.stateLength)
                else -> throw IllegalArgumentException("stateLength must not be less than ${Defaults.STATE_MIN_LENGTH}")
            }

            EndSessionFlow(
                client = client,
                request = request,
                state = state,
                endpoint = it,
            )
        }
    }
}
