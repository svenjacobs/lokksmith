package dev.lokksmith.client.discovery

import dev.lokksmith.LokksmithException

public class MetadataDiscoveryException internal constructor(cause: Throwable? = null) :
    LokksmithException(cause = cause)