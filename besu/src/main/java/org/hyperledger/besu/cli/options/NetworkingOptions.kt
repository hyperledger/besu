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
package org.hyperledger.besu.cli.options

import org.hyperledger.besu.cli.options.OptionParser.format
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration
import picocli.CommandLine
import java.util.*

/** The Networking Cli options.  */
class NetworkingOptions private constructor() : CLIOptions<NetworkingConfiguration?> {

    @CommandLine.Option(
        names = [Companion.INITIATE_CONNECTIONS_FREQUENCY_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["The frequency (in seconds) at which to initiate new outgoing connections (default: \${DEFAULT-VALUE})"]
    )
    private var initiateConnectionsFrequencySec = NetworkingConfiguration.DEFAULT_INITIATE_CONNECTIONS_FREQUENCY_SEC

    @CommandLine.Option(
        names = [Companion.CHECK_MAINTAINED_CONNECTIONS_FREQUENCY_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["The frequency (in seconds) at which to check maintained connections (default: \${DEFAULT-VALUE})"]
    )
    private var checkMaintainedConnectionsFrequencySec =
        NetworkingConfiguration.DEFAULT_CHECK_MAINTAINED_CONNECTIONS_FREQUENCY_SEC

    @CommandLine.Option(
        names = [Companion.DNS_DISCOVERY_SERVER_OVERRIDE_FLAG],
        hidden = true,
        description = ["DNS server host to use for doing DNS Discovery of peers, rather than the machine's configured DNS server"]
    )
    private var dnsDiscoveryServerOverride = Optional.empty<String>()

    @CommandLine.Option(
        names = [Companion.DISCOVERY_PROTOCOL_V5_ENABLED],
        hidden = true,
        description = ["Whether to enable P2P Discovery Protocol v5 (default: \${DEFAULT-VALUE})"]
    )
    private val isPeerDiscoveryV5Enabled = false

    @CommandLine.Option(
        names = [FILTER_ON_ENR_FORK_ID],
        hidden = true,
        description = ["Whether to enable filtering of peers based on the ENR field ForkId)"]
    )
    private val filterOnEnrForkId = NetworkingConfiguration.DEFAULT_FILTER_ON_ENR_FORK_ID

    override fun toDomainObject(): NetworkingConfiguration {
        val config = NetworkingConfiguration.create()
        config.setCheckMaintainedConnectionsFrequency(checkMaintainedConnectionsFrequencySec)
        config.setInitiateConnectionsFrequency(initiateConnectionsFrequencySec)
        config.setDnsDiscoveryServerOverride(dnsDiscoveryServerOverride)
        config.discovery.isDiscoveryV5Enabled = isPeerDiscoveryV5Enabled
        config.discovery.setFilterOnEnrForkId(filterOnEnrForkId)
        return config
    }

    override fun getCLIOptions(): List<String> {
        val retval =
            Arrays.asList(
                Companion.CHECK_MAINTAINED_CONNECTIONS_FREQUENCY_FLAG,
                format(checkMaintainedConnectionsFrequencySec),
                Companion.INITIATE_CONNECTIONS_FREQUENCY_FLAG,
                format(initiateConnectionsFrequencySec)
            )

        if (dnsDiscoveryServerOverride.isPresent) {
            retval.add(Companion.DNS_DISCOVERY_SERVER_OVERRIDE_FLAG)
            retval.add(dnsDiscoveryServerOverride.get())
        }
        return retval
    }

    companion object {
        /** The constant FILTER_ON_ENR_FORK_ID.  */
        const val FILTER_ON_ENR_FORK_ID: String = "--filter-on-enr-fork-id"

        /**
         * Create networking options.
         *
         * @return the networking options
         */
        @JvmStatic
        fun create(): NetworkingOptions {
            return NetworkingOptions()
        }

        /**
         * Create networking options from Networking Configuration.
         *
         * @param networkingConfig the networking config
         * @return the networking options
         */
        @JvmStatic
        fun fromConfig(networkingConfig: NetworkingConfiguration): NetworkingOptions {
            val cliOptions = NetworkingOptions()
            cliOptions.checkMaintainedConnectionsFrequencySec =
                networkingConfig.checkMaintainedConnectionsFrequencySec
            cliOptions.initiateConnectionsFrequencySec =
                networkingConfig.initiateConnectionsFrequencySec
            cliOptions.dnsDiscoveryServerOverride = networkingConfig.dnsDiscoveryServerOverride

            return cliOptions
        }

        private const val INITIATE_CONNECTIONS_FREQUENCY_FLAG = "--Xp2p-initiate-connections-frequency"
        private const val CHECK_MAINTAINED_CONNECTIONS_FREQUENCY_FLAG = "--Xp2p-check-maintained-connections-frequency"
        private const val DNS_DISCOVERY_SERVER_OVERRIDE_FLAG = "--Xp2p-dns-discovery-server"
        private const val DISCOVERY_PROTOCOL_V5_ENABLED = "--Xv5-discovery-enabled"
    }
}
