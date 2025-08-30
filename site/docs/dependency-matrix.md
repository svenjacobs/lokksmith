# Dependency Matrix

The tables below detail the dependencies for Lokksmith artifacts. While Lokksmith may be compatible
with other (minor) versions of transitive dependencies, use them with caution.

## lokksmith-core

| Version       |   Kotlin   | Coroutines | Serialization |   Ktor    |
|---------------|:----------:|:----------:|:-------------:|:---------:|
| 0.1.x         |   2.1.21   | **1.10.2** |     1.8.1     |   3.1.3   |
| 0.2.x - 0.3.0 |   2.2.0    |     "      |       "       |     "     |
| 0.3.1 - 0.3.2 |     "      |     "      |   **1.9.0**   |     "     |
| 0.3.3         |     "      |     "      |       "       | **3.2.1** |
| 0.3.4 - 0.3.7 |     "      |     "      |       "       | **3.2.3** |
| 0.3.8 - 0.4.x | **2.2.10** |     "      |       "       |     "     |

## lokksmith-compose

Includes all dependencies from `lokksmith-core` plus:

| Version       | Compose Multiplatform |
|---------------|:---------------------:| 
| 0.3.x - 0.4.x |       **1.8.2**       | 
