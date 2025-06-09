package dev.lokksmith.client.request.flow

internal interface AuthFlowResponseHandler {

    suspend fun onResponse(redirectUri: String)
}