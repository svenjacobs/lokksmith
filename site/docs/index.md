# About

Lokksmith is an opinionated[^1], modern Kotlin Multiplatform library for implementing the OpenID Connect
Authorization Code Flow (with PKCE) on Android and iOS. The library offers a simple, concise and
intuitive API with sensible defaults, while remaining configurable for advanced use cases. 
We prioritize adherence to secure, recommended standards and patterns.

## Non-goals

- Full-featured OIDC / OAuth 2.0 client implementing all features of the specifications
- Authorization via (embedded) web views, which are considered insecure
- Providing a viable or user-friendly API for non-Kotlin consumers (for example Java)
- Compatibility with the legacy Android view system (though developers may implement their own
  integration if needed)

[^1]: This means we use libraries such as KotlinX Serialization, Ktor, and DataStore, and do not 
      intend to make them interchangeable with other solutions.

*[OIDC]: OpenID Connect
*[PKCE]: Proof Key for Code Exchange
