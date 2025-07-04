# Dependency Matrix

The tables below detail the dependencies for Lokksmith artifacts. While Lokksmith may be compatible
with other (minor) versions of transitive dependencies, use them with caution.

## lokksmith-core

| Version | Kotlin       | Coroutines | Serialization | Ktor  |
|---------|--------------|------------|---------------|-------|
| 0.1.x   | 2.1.21       | 1.10.2     | 1.8.1         | 3.1.3 | 
| 0.2.x   | ⬆️ **2.2.0** | 1.10.2     | 1.8.1         | 3.1.3 | 
| 0.3.0   | 2.2.0        | 1.10.2     | 1.8.1         | 3.1.3 | 

## lokksmith-compose

Includes all dependencies from `lokksmith-core` plus:

| Version | Compose Multiplatform |
|---------|-----------------------| 
| 0.3.0   | 1.8.2                 | 
