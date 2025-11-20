# Comparison

The following table compares Lokksmith with the popular [AppAuth-Android](https://github.com/openid/AppAuth-Android)
library.

|                                                 | Lokksmith                                                  | AppAuth                                                                                                       |
|-------------------------------------------------|------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| First Release                                   | Jun 9, 2025                                                | Feb 26, 2016                                                                                                  |
| Current Release                                 | `{{ lokksmith_version }}`<br/>{{ lokksmith_version_date }} | `0.11.1`<br/>Dec 22, 2021                                                                                     |
| Kotlin                                          | Yes                                                        | No                                                                                                            |
| Coroutines &amp; Flows                          | Yes                                                        | No                                                                                                            |
| Multiplatform                                   | Yes (Android &amp; iOS)                                    | No, separate [iOS library](https://github.com/openid/AppAuth-iOS)                                             |
| Compose Multiplatform                           | Yes                                                        | No                                                                                                            |
| Core Functionality independent<br/>of Mobile OS | Yes                                                        | No                                                                                                            |
| Client State Persistence                        | Yes                                                        | No                                                                                                            |
| Multiple Clients Management                     | Yes                                                        | No                                                                                                            |
| Supports HTTP/2 and beyond                      | Yes, through [Ktor](https://ktor.io)                       | No, uses Android's [built-in HTTP client](https://developer.android.com/reference/java/net/HttpURLConnection) |
| Supports Migration                              | Yes                                                        | No                                                                                                            |
