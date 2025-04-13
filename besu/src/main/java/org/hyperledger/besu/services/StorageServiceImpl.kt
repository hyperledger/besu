/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.services

import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier
import org.hyperledger.besu.plugin.services.StorageService
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageFactory
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** The Storage service implementation.  */
class StorageServiceImpl @Inject constructor() : StorageService {
    private val segments: List<SegmentIdentifier> =
        java.util.List.of<SegmentIdentifier>(*KeyValueSegmentIdentifier.entries.toTypedArray())
    private val factories: MutableMap<String, KeyValueStorageFactory> = ConcurrentHashMap()

    override fun registerKeyValueStorage(factory: KeyValueStorageFactory) {
        factories[factory.name] = factory
    }

    override fun getAllSegmentIdentifiers(): List<SegmentIdentifier> {
        return segments
    }

    override fun getByName(name: String): Optional<KeyValueStorageFactory> {
        return Optional.ofNullable(factories[name])
    }
}
