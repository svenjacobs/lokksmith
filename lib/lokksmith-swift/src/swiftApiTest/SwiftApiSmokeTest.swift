/*
 * Copyright 2026 Sven Jacobs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Type-checked against the assembled XCFramework by the `swiftApiSmokeTest` Gradle task, which
// `check` depends on. It verifies that the exported surface stays usable from idiomatic Swift —
// async/await, synchronous properties, closures, enums and error handling — and it mirrors the
// snippets in site/docs/getting-started/usage.md, so those cannot silently rot.

import Foundation
import Lokksmith

let lokksmith = LokksmithManager()

func signIn() async throws -> LokksmithTokens? {
    let client = try await lokksmith.getOrCreateClient(
        key: "main",
        configuration: .companion.discovery(
            clientId: "my-client-id",
            discoveryUrl: "https://example.com/.well-known/openid-configuration"
        )
    )

    // Synchronous reads, no suspension and no network.
    let alreadySignedIn: Bool = client.isAuthenticated
    let subject: String? = client.tokens?.idToken.subject
    _ = (alreadySignedIn, subject)

    let request = LokksmithAuthorizationRequest(redirectUri: "my-app://openid-response")
    request.scopes = ["profile", "email"]
    request.prompts = [.login]
    request.uiLocales = ["de"]

    // nil means the user dismissed the browser.
    return try await client.authorize(request: request)
}

func accessToken(for client: LokksmithClient) async -> String? {
    do {
        return try await client.freshTokens().accessToken.token
    } catch let error as NSError {
        let failure = error.userInfo["KotlinException"] as? LokksmithFailure
        switch failure?.kind {
        case .oAuthRejection:
            _ = try? await client.resetTokens()
            return nil
        case .transport:
            return nil // transient, keep the session
        default:
            return nil
        }
    }
}

func observe(client: LokksmithClient) -> LokksmithCancellable {
    client.observeTokens { tokens in
        print("signed in: \(tokens != nil)")
    }
}

func signOut(client: LokksmithClient) async throws {
    let request = LokksmithEndSessionRequest(redirectUri: "my-app://openid-response")
    request.logoutHint = "user@example.com"
    _ = try await client.endSession(request: request)
    _ = try await client.resetTokens()
}

func migrateOffline(legacyIdToken: String) async throws {
    // Offline configuration: endpoints supplied directly, so no discovery request is made.
    let metadata = LokksmithMetadata(
        issuer: "https://example.com",
        authorizationEndpoint: "https://example.com/oauth/authorize",
        tokenEndpoint: "https://example.com/oauth/token"
    )
    let client = try await lokksmith.getOrCreateClient(
        key: "main",
        configuration: .companion.metadata(clientId: "my-client-id", metadata: metadata)
    )
    guard !lokksmith.migration.isMigrated(client: client) else { return }
    try await lokksmith.migration.setTokens(
        client: client,
        accessToken: "legacy-access-token",
        accessTokenExpiresAt: 1_700_000_000,
        refreshToken: "legacy-refresh-token",
        refreshTokenExpiresAt: nil,
        idToken: legacyIdToken
    )
    _ = try await client.refresh()
}
