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
package org.hyperledger.besu.services.kvstore

import org.hyperledger.besu.plugin.BesuPlugin
import org.hyperledger.besu.plugin.ServiceManager
import org.hyperledger.besu.plugin.services.BesuConfiguration
import org.hyperledger.besu.plugin.services.MetricsSystem
import org.hyperledger.besu.plugin.services.StorageService
import org.hyperledger.besu.plugin.services.exception.StorageException
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageFactory
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** The In memory storage plugin.  */
class InMemoryStoragePlugin
/** Default constructor  */
    : BesuPlugin {
    private var context: ServiceManager? = null
    private var factory: InMemoryKeyValueStorageFactory? = null
    private var privacyFactory: InMemoryKeyValueStorageFactory? = null

    override fun register(context: ServiceManager) {
        LOG.debug("Registering plugin")
        this.context = context

        createFactoriesAndRegisterWithStorageService()

        LOG.debug("Plugin registered.")
    }

    override fun start() {
        LOG.debug("Starting plugin.")
        if (factory == null) {
            createFactoriesAndRegisterWithStorageService()
        }
    }

    override fun stop() {
        LOG.debug("Stopping plugin.")

        if (factory != null) {
            factory!!.close()
            factory = null
        }

        if (privacyFactory != null) {
            privacyFactory!!.close()
            privacyFactory = null
        }
    }

    private fun createAndRegister(service: StorageService) {
        factory = InMemoryKeyValueStorageFactory("memory")
        privacyFactory = InMemoryKeyValueStorageFactory("memory-privacy")

        service.registerKeyValueStorage(factory)
        service.registerKeyValueStorage(privacyFactory)
    }

    private fun createFactoriesAndRegisterWithStorageService() {
        context!!
            .getService(StorageService::class.java)
            .ifPresentOrElse(
                { service: Any -> createAndRegister(service as StorageService) },
                { LOG.error("Failed to register KeyValueFactory due to missing StorageService.") })
    }

    /** The Memory key value storage factory.  */
    class InMemoryKeyValueStorageFactory
    /**
     * Instantiates a new Memory key value storage factory.
     *
     * @param name the name
     */(private val name: String) : KeyValueStorageFactory {
        private val storageMap: MutableMap<List<SegmentIdentifier>, SegmentedInMemoryKeyValueStorage> = HashMap()

        override fun getName(): String {
            return name
        }

        @Throws(StorageException::class)
        override fun create(
            segment: SegmentIdentifier,
            configuration: BesuConfiguration,
            metricsSystem: MetricsSystem
        ): KeyValueStorage {
            val kvStorage =
                storageMap.computeIfAbsent(
                    java.util.List.of(segment)
                ) { seg: List<SegmentIdentifier>? -> SegmentedInMemoryKeyValueStorage(seg) }
            return SegmentedKeyValueStorageAdapter(segment, kvStorage)
        }

        @Throws(StorageException::class)
        override fun create(
            segments: List<SegmentIdentifier>,
            configuration: BesuConfiguration,
            metricsSystem: MetricsSystem
        ): SegmentedKeyValueStorage {
            val kvStorage =
                storageMap.computeIfAbsent(segments) { `__`: List<SegmentIdentifier>? -> SegmentedInMemoryKeyValueStorage() }
            return kvStorage
        }

        override fun isSegmentIsolationSupported(): Boolean {
            return true
        }

        override fun isSnapshotIsolationSupported(): Boolean {
            return true
        }

        override fun close() {
            storageMap.clear()
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(InMemoryStoragePlugin::class.java)
    }
}
