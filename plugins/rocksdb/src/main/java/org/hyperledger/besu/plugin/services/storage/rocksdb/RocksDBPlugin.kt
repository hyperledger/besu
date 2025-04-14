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
package org.hyperledger.besu.plugin.services.storage.rocksdb

import com.google.common.base.Suppliers
import org.hyperledger.besu.plugin.BesuPlugin
import org.hyperledger.besu.plugin.ServiceManager
import org.hyperledger.besu.plugin.services.BesuService
import org.hyperledger.besu.plugin.services.PicoCLIOptions
import org.hyperledger.besu.plugin.services.StorageService
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

/** The RocksDb plugin.  */
class RocksDBPlugin : BesuPlugin {
    private val options: RocksDBCLIOptions = RocksDBCLIOptions.create()
    private val ignorableSegments: MutableList<SegmentIdentifier> = ArrayList()
    private var context: ServiceManager? = null
    private var factory: RocksDBKeyValueStorageFactory? = null
    private var privacyFactory: RocksDBKeyValuePrivacyStorageFactory? = null

    /**
     * Add ignorable segment identifier.
     *
     * @param ignorable the ignorable
     */
    fun addIgnorableSegmentIdentifier(ignorable: SegmentIdentifier) {
        ignorableSegments.add(ignorable)
    }

    override fun register(context: ServiceManager) {
        LOG.debug("Registering plugin")
        this.context = context

        val cmdlineOptions: Optional<out BesuService> = context.getService(PicoCLIOptions::class.java)

        check(!cmdlineOptions.isEmpty) { "Expecting a PicoCLI options to register CLI options with, but none found." }

        (cmdlineOptions.get() as PicoCLIOptions).addPicoCLIOptions(NAME, options)
        createFactoriesAndRegisterWithStorageService()

        LOG.debug("Plugin registered.")
    }

    override fun start() {
        LOG.debug("Starting plugin.")
        if (factory == null) {
            LOG.trace("Applied configuration: {}", options.toString())
            createFactoriesAndRegisterWithStorageService()
        }
    }

    override fun stop() {
        LOG.debug("Stopping plugin.")

        try {
            if (factory != null) {
                factory!!.close()
                factory = null
            }
        } catch (e: IOException) {
            LOG.error("Failed to stop plugin: {}", e.message, e)
        }

        try {
            if (privacyFactory != null) {
                privacyFactory!!.close()
                privacyFactory = null
            }
        } catch (e: IOException) {
            LOG.error("Failed to stop plugin: {}", e.message, e)
        }
    }

    val isHighSpecEnabled: Boolean
        /**
         * Is high spec enabled.
         *
         * @return the boolean
         */
        get() = options.isHighSpec

    private fun createAndRegister(service: StorageService) {
        val segments = service.allSegmentIdentifiers

        val configuration =
            Suppliers.memoize { options.toDomainObject() }
        factory =
            RocksDBKeyValueStorageFactory(
                configuration,
                segments,
                ignorableSegments,
                RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS
            )
        privacyFactory = RocksDBKeyValuePrivacyStorageFactory(factory)

        service.registerKeyValueStorage(factory)
        service.registerKeyValueStorage(privacyFactory)
    }

    private fun createFactoriesAndRegisterWithStorageService() {
        context!!
            .getService(StorageService::class.java)
            .ifPresentOrElse(
                { service: StorageService -> this.createAndRegister(service) } as ((BesuService) -> Unit)?,
                Runnable { LOG.error("Failed to register KeyValueFactory due to missing StorageService.") })
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(RocksDBPlugin::class.java)
        private const val NAME = "rocksdb"
    }
}
