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
package org.hyperledger.besu.cli.config

import org.hyperledger.besu.config.GenesisConfig
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl
import org.hyperledger.besu.plugin.data.EnodeURL
import java.io.IOException
import java.math.BigInteger
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.stream.Collectors

/**
 * The Eth network config.
 *
 * @param genesisConfig Genesis Config File
 * @param networkId Network Id
 * @param bootNodes Boot Nodes
 * @param dnsDiscoveryUrl DNS Discovery URL
 */
@JvmRecord
data class EthNetworkConfig(
  @JvmField val genesisConfig: GenesisConfig,
  @JvmField val networkId: BigInteger,
  @JvmField val bootNodes: List<EnodeURL>,
  @JvmField val dnsDiscoveryUrl: String
) {
    /** The type Builder.  */
    class Builder(ethNetworkConfig: EthNetworkConfig) {
        private var dnsDiscoveryUrl: String
        private var genesisConfig: GenesisConfig
        private var networkId: BigInteger
        private var bootNodes: List<EnodeURL>

        /**
         * Instantiates a new Builder.
         *
         * @param ethNetworkConfig the eth network config
         */
        init {
            this.genesisConfig = ethNetworkConfig.genesisConfig
            this.networkId = ethNetworkConfig.networkId
            this.bootNodes = ethNetworkConfig.bootNodes
            this.dnsDiscoveryUrl = ethNetworkConfig.dnsDiscoveryUrl
        }

        /**
         * Sets genesis config file.
         *
         * @param genesisConfig the genesis config
         * @return this builder
         */
        fun setGenesisConfig(genesisConfig: GenesisConfig): Builder {
            this.genesisConfig = genesisConfig
            return this
        }

        /**
         * Sets network id.
         *
         * @param networkId the network id
         * @return this builder
         */
        fun setNetworkId(networkId: BigInteger): Builder {
            this.networkId = networkId
            return this
        }

        /**
         * Sets boot nodes.
         *
         * @param bootNodes the boot nodes
         * @return this builder
         */
        fun setBootNodes(bootNodes: List<EnodeURL>): Builder {
            this.bootNodes = bootNodes
            return this
        }

        /**
         * Sets dns discovery url.
         *
         * @param dnsDiscoveryUrl the dns discovery url
         * @return this builder
         */
        fun setDnsDiscoveryUrl(dnsDiscoveryUrl: String): Builder {
            this.dnsDiscoveryUrl = dnsDiscoveryUrl
            return this
        }

        /**
         * Build eth network config.
         *
         * @return the eth network config
         */
        fun build(): EthNetworkConfig {
            return EthNetworkConfig(genesisConfig, networkId, bootNodes, dnsDiscoveryUrl)
        }
    }

    /**
     * Validate parameters on new record creation
     *
     * @param genesisConfig the genesis config
     * @param networkId the network id
     * @param bootNodes the boot nodes
     * @param dnsDiscoveryUrl the dns discovery url
     */
    init {
        Objects.requireNonNull(genesisConfig)
        Objects.requireNonNull(bootNodes)
    }

    companion object {
        /**
         * Gets network config.
         *
         * @param networkName the network name
         * @return the network config
         */
        @JvmStatic
        fun getNetworkConfig(networkName: NetworkName): EthNetworkConfig {
            val genesisSource = jsonConfigSource(networkName.genesisFile)
            val genesisConfig = GenesisConfig.fromSource(genesisSource)
            val genesisConfigOptions = genesisConfig.configOptions
            val rawBootNodes =
                genesisConfigOptions.discoveryOptions.bootNodes
            val bootNodes =
                rawBootNodes
                    .map { strings: List<String?> ->
                        strings.stream().map { value: String? -> EnodeURLImpl.fromString(value) }
                            .collect(Collectors.toList())
                    }
                    .orElse(emptyList())

            return EthNetworkConfig(
                genesisConfig,
                networkName.networkId,
                bootNodes,
                genesisConfigOptions.discoveryOptions.discoveryDnsUrl.orElse(null)
            )
        }

        private fun jsonConfigSource(resourceName: String): URL? {
            return EthNetworkConfig::class.java.getResource(resourceName)
        }

        /**
         * Json config string.
         *
         * @param network the named network
         * @return the json string
         */
        @JvmStatic
        fun jsonConfig(network: NetworkName): String {
            try {
                EthNetworkConfig::class.java.getResourceAsStream(network.genesisFile).use { genesisFileInputStream ->
                    return String(genesisFileInputStream.readAllBytes(), StandardCharsets.UTF_8)
                }
            } catch (e: IOException) {
                throw IllegalStateException(e)
            } catch (e: NullPointerException) {
                throw IllegalStateException(e)
            }
        }
    }
}
