package dev.lokksmith

import dev.lokksmith.client.Client

/**
 * Context for creating a new [Client].
 *
 * [id] must be specified as well as one of [discoveryUrl] or [metadata].
 * [options] can be used to tweak the behaviour of the client.
 *
 * @see Lokksmith.create
 * @see Lokksmith.getOrCreate
 */
public class CreateContext internal constructor(
    internal val props: Properties = Properties(),
) {
    internal data class Properties(
        var id: String? = null,
        var metadata: Client.Metadata? = null,
        var discoveryUrl: String? = null,
        var options: Client.Options = Client.Options(),
    )

    internal fun validate() {
        requireNotNull(props.id) { "id must be specified" }
        require((props.discoveryUrl == null) != (props.metadata == null)) {
            "either discoveryUrl or metadata must be specified, but not both"
        }
    }
}

public var CreateContext.id: String
    get() = throw UnsupportedOperationException("id cannot be read")
    set(value) {
        props.id = value
    }

public var CreateContext.discoveryUrl: String
    get() = throw UnsupportedOperationException("discoveryUrl cannot be read")
    set(value) {
        props.discoveryUrl = value
    }

public var CreateContext.metadata: Client.Metadata
    get() = throw UnsupportedOperationException("metadata cannot be read")
    set(value) {
        props.metadata = value
    }

/**
 * Options for configuring the behaviour of the client.
 */
public var CreateContext.options: Client.Options
    get() = throw UnsupportedOperationException("options cannot be read")
    set(value) {
        props.options = value
    }
