/*
 * Copyright 2025 Sven Jacobs
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
package dev.lokksmith.client.snapshot

import dev.lokksmith.client.Key
import kotlinx.coroutines.flow.Flow

internal class SnapshotStoreSpy(internal val subject: InternalSnapshotStore) :
    InternalSnapshotStore by subject {

    data class ObserveCall(val key: Key)

    data class GetForStateCall(val state: String)

    data class SetCall(val key: Key, val snapshot: Snapshot)

    data class InternalSetCall(val key: Key, val snapshot: Snapshot)

    data class DeleteCall(val key: Key)

    data class ExistsCall(val key: Key)

    val observeCalls = mutableListOf<ObserveCall>()
    val getForStateCalls = mutableListOf<GetForStateCall>()
    val setCalls = mutableListOf<SetCall>()
    val internalSetCalls = mutableListOf<InternalSetCall>()
    val deleteCalls = mutableListOf<DeleteCall>()
    val existsCalls = mutableListOf<ExistsCall>()

    override fun observe(key: Key): Flow<Snapshot?> {
        observeCalls.add(ObserveCall(key))
        return subject.observe(key)
    }

    override suspend fun getForState(state: String): Snapshot? {
        getForStateCalls.add(GetForStateCall(state))
        return subject.getForState(state)
    }

    override suspend fun set(key: Key, snapshot: Snapshot): Snapshot {
        setCalls.add(SetCall(key, snapshot))
        return subject.set(key, snapshot)
    }

    override suspend fun internalSet(key: Key, snapshot: Snapshot): Snapshot {
        internalSetCalls.add(InternalSetCall(key, snapshot))
        return subject.internalSet(key, snapshot)
    }

    override suspend fun delete(key: Key): Boolean {
        deleteCalls.add(DeleteCall(key))
        return subject.delete(key)
    }

    override suspend fun exists(key: Key): Boolean {
        existsCalls.add(ExistsCall(key))
        return subject.exists(key)
    }
}
