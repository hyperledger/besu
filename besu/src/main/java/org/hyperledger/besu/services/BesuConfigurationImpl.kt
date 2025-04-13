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

import org.hyperledger.besu.cli.options.JsonRpcHttpOptions
import org.hyperledger.besu.datatypes.Wei
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration
import org.hyperledger.besu.ethereum.core.MiningConfiguration
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration
import org.hyperledger.besu.plugin.services.BesuConfiguration
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat
import java.nio.file.Path
import java.util.*

/** A concrete implementation of BesuConfiguration which is used in Besu plugin framework.  */
class BesuConfigurationImpl
/** Default Constructor.  */
    : BesuConfiguration {
    private var storagePath: Path? = null
    private var dataPath: Path? = null
    private var dataStorageConfiguration: DataStorageConfiguration? = null

    // defaults
    private var miningConfiguration: MiningConfiguration? = null
    private var rpcHttpHost = JsonRpcConfiguration.DEFAULT_JSON_RPC_HOST
    private var rpcHttpPort = JsonRpcConfiguration.DEFAULT_JSON_RPC_PORT

    /**
     * Post creation initialization
     *
     * @param dataPath The Path representing data folder
     * @param storagePath The path representing storage folder
     * @param dataStorageConfiguration The data storage configuration
     * @return BesuConfigurationImpl instance
     */
    fun init(
        dataPath: Path?,
        storagePath: Path?,
        dataStorageConfiguration: DataStorageConfiguration?
    ): BesuConfigurationImpl {
        this.dataPath = dataPath
        this.storagePath = storagePath
        this.dataStorageConfiguration = dataStorageConfiguration
        return this
    }

    /**
     * Set the mining parameters
     *
     * @param miningConfiguration configured mining parameters
     * @return BesuConfigurationImpl instance
     */
    fun withMiningParameters(miningConfiguration: MiningConfiguration?): BesuConfigurationImpl {
        this.miningConfiguration = miningConfiguration
        return this
    }

    /**
     * Set the RPC http options
     *
     * @param rpcHttpOptions configured rpc http options
     * @return BesuConfigurationImpl instance
     */
    fun withJsonRpcHttpOptions(rpcHttpOptions: JsonRpcHttpOptions): BesuConfigurationImpl {
        this.rpcHttpHost = rpcHttpOptions.rpcHttpHost
        this.rpcHttpPort = rpcHttpOptions.rpcHttpPort
        return this
    }

    @Deprecated("")
    override fun getRpcHttpHost(): Optional<String> {
        return Optional.of(rpcHttpHost)
    }

    @Deprecated("")
    override fun getRpcHttpPort(): Optional<Int> {
        return Optional.of(rpcHttpPort)
    }

    override fun getConfiguredRpcHttpHost(): String {
        return rpcHttpHost
    }

    override fun getConfiguredRpcHttpPort(): Int {
        return rpcHttpPort
    }

    override fun getStoragePath(): Path {
        return storagePath!!
    }

    override fun getDataPath(): Path {
        return dataPath!!
    }

    override fun getDatabaseFormat(): DataStorageFormat {
        return dataStorageConfiguration!!.dataStorageFormat
    }

    override fun getMinGasPrice(): Wei {
        return miningConfiguration!!.minTransactionGasPrice
    }

    override fun getDataStorageConfiguration(): org.hyperledger.besu.plugin.services.storage.DataStorageConfiguration {
        return DataStoreConfigurationImpl(dataStorageConfiguration)
    }

    /**
     * A concrete implementation of DataStorageConfiguration which is used in Besu plugin framework.
     */
    class DataStoreConfigurationImpl

    /**
     * Instantiate the concrete implementation of the plugin DataStorageConfiguration.
     *
     * @param dataStorageConfiguration The Ethereum core module data storage configuration
     */(private val dataStorageConfiguration: DataStorageConfiguration?) :
        org.hyperledger.besu.plugin.services.storage.DataStorageConfiguration {
        override fun getDatabaseFormat(): DataStorageFormat {
            return dataStorageConfiguration!!.dataStorageFormat
        }

        override fun getReceiptCompactionEnabled(): Boolean {
            return dataStorageConfiguration!!.receiptCompactionEnabled
        }
    }
}
