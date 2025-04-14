/*
 * Copyright contributors to Hyperledger Besu.
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

import com.google.common.net.InetAddresses
import org.apache.commons.net.util.SubnetUtils.SubnetInfo
import org.apache.tuweni.bytes.Bytes
import org.hyperledger.besu.cli.DefaultCommandValues
import org.hyperledger.besu.cli.converter.PercentageConverter
import org.hyperledger.besu.cli.converter.SubnetInfoConverter
import org.hyperledger.besu.cli.util.CommandLineUtils
import org.hyperledger.besu.ethereum.p2p.discovery.P2PDiscoveryConfiguration
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl
import org.hyperledger.besu.util.NetworkUtility
import org.hyperledger.besu.util.number.Fraction
import org.hyperledger.besu.util.number.Percentage
import picocli.CommandLine
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException
import java.util.*
import java.util.stream.Collectors

/** Command line options for configuring P2P discovery on the node.  */
class P2PDiscoveryOptions
/** Default constructor  */
    : CLIOptions<P2PDiscoveryConfiguration?> {
    /** Functional interface for checking if a network interface is available.  */
    fun interface NetworkInterfaceChecker {
        /**
         * Checks if the provided network interface is available.
         *
         * @param p2pInterface The network interface to check.
         * @return True if the network interface is available, false otherwise.
         * @throws UnknownHostException If the host is unknown.
         * @throws SocketException If there is an error with the socket.
         */
        @Throws(UnknownHostException::class, SocketException::class)
        fun isNetworkInterfaceAvailable(p2pInterface: String?): Boolean
    }

    // Public IP stored to prevent having to research it each time we need it.
    private var autoDiscoveredDefaultIP: InetAddress? = null

    /** Completely disables P2P within Besu.  */
    @JvmField
    @CommandLine.Option(
        names = ["--p2p-enabled"],
        description = ["Enable P2P functionality (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    var p2pEnabled: Boolean = true

    /** Boolean option to indicate if peers should be discovered.  */
    @JvmField
    @CommandLine.Option(
        names = ["--discovery-enabled"],
        description = ["Enable P2P discovery (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    var peerDiscoveryEnabled: Boolean = true

    /**
     * A list of bootstrap nodes can be passed and a hardcoded list will be used otherwise by the
     * Runner.
     */
    // NOTE: we have no control over default value here.
    @JvmField
    @CommandLine.Option(
        names = ["--bootnodes"],
        paramLabel = "<enode://id@host:port>",
        description = [("Comma separated enode URLs for P2P discovery bootstrap. "
                + "Default is a predefined list.")],
        split = ",",
        arity = "0..*"
    )
    var bootNodes: List<String>? = null

    /** The IP the node advertises to peers for P2P communication.  */
    // PicoCLI requires non-final Strings.
    @JvmField
    @CommandLine.Option(
        names = ["--p2p-host"],
        paramLabel = DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP,
        description = ["IP address this node advertises to its peers (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    var p2pHost: String = autoDiscoverDefaultIP()!!.hostAddress

    /** The network interface address on which this node listens for P2P communication.  */
    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--p2p-interface"],
        paramLabel = DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP,
        description = ["The network interface address on which this node listens for P2P communication (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    var p2pInterface: String = NetworkUtility.INADDR_ANY

    /** The port on which this node listens for P2P communication.  */
    @JvmField
    @CommandLine.Option(
        names = ["--p2p-port"],
        paramLabel = DefaultCommandValues.MANDATORY_PORT_FORMAT_HELP,
        description = ["Port on which to listen for P2P communication (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    val p2pPort: Int = EnodeURLImpl.DEFAULT_LISTENING_PORT

    /** The maximum number of peers this node can connect to.  */
    @JvmField
    @CommandLine.Option(
        names = ["--max-peers", "--p2p-peer-upper-bound"],
        paramLabel = DefaultCommandValues.MANDATORY_INTEGER_FORMAT_HELP,
        description = ["Maximum P2P connections that can be established (default: \${DEFAULT-VALUE})"]
    )
    var maxPeers: Int = DefaultCommandValues.DEFAULT_MAX_PEERS

    /** Boolean option to limit the number of P2P connections initiated remotely.  */
    @JvmField
    @CommandLine.Option(
        names = ["--remote-connections-limit-enabled"],
        description = ["Whether to limit the number of P2P connections initiated remotely. (default: \${DEFAULT-VALUE})"]
    )
    var isLimitRemoteWireConnectionsEnabled: Boolean = true

    /** The maximum percentage of P2P connections that can be initiated remotely.  */
    @JvmField
    @CommandLine.Option(
        names = ["--remote-connections-max-percentage"],
        paramLabel = DefaultCommandValues.MANDATORY_DOUBLE_FORMAT_HELP,
        description = ["The maximum percentage of P2P connections that can be initiated remotely. Must be between 0 and 100 inclusive. (default: \${DEFAULT-VALUE})"],
        arity = "1",
        converter = [PercentageConverter::class]
    )
    var maxRemoteConnectionsPercentage: Percentage =
        Fraction.fromFloat(DefaultCommandValues.DEFAULT_FRACTION_REMOTE_WIRE_CONNECTIONS_ALLOWED)
            .toPercentage()

    /** The URL to use for DNS discovery.  */
    // PicoCLI requires non-final Strings.
    @JvmField
    @CommandLine.Option(names = ["--discovery-dns-url"], description = ["Specifies the URL to use for DNS discovery"])
    var discoveryDnsUrl: String? = null

    /** Boolean option to allow for incoming connections to be prioritized randomly.  */
    @JvmField
    @CommandLine.Option(
        names = ["--random-peer-priority-enabled"],
        description = ["Allow for incoming connections to be prioritized randomly. This will prevent (typically small, stable) networks from forming impenetrable peer cliques. (default: \${DEFAULT-VALUE})"]
    )
    var randomPeerPriority: Boolean = java.lang.Boolean.FALSE

    /** A list of node IDs to ban from the P2P network.  */
    @CommandLine.Option(
        names = ["--banned-node-ids", "--banned-node-id"],
        paramLabel = DefaultCommandValues.MANDATORY_NODE_ID_FORMAT_HELP,
        description = ["A list of node IDs to ban from the P2P network."],
        split = ",",
        arity = "1..*"
    )
    fun setBannedNodeIds(values: List<String>) {
        try {
            bannedNodeIds =
                values.stream()
                    .filter { value: String -> !value.isEmpty() }
                    .map { nodeId: String? -> EnodeURLImpl.parseNodeId(nodeId) }
                    .collect(Collectors.toList())
        } catch (e: IllegalArgumentException) {
            throw CommandLine.ParameterException(
                CommandLine(this), "Invalid ids supplied to '--banned-node-ids'. " + e.message
            )
        }
    }

    // Boolean option to set that in a PoA network the bootnodes should always be queried during
    // peer table refresh. If this flag is disabled bootnodes are only sent FINDN requests on first
    // startup, meaning that an offline bootnode or network outage at the client can prevent it
    // discovering any peers without a restart.
    @CommandLine.Option(
        names = ["--poa-discovery-retry-bootnodes"],
        description = [("Always use of bootnodes for discovery in PoA networks. Disabling this reverts "
                + " to the same behaviour as non-PoA networks, where neighbours are only discovered from bootnodes on first startup."
                + "(default: \${DEFAULT-VALUE})")],
        arity = "1"
    )
    private var poaDiscoveryRetryBootnodes = true

    private var bannedNodeIds: Collection<Bytes> = ArrayList()

    /**
     * Auto-discovers the default IP of the client.
     *
     * @return machine loopback address
     */
    // Loopback IP is used by default as this is how smokeTests require it to be
    // and it's probably a good security behaviour to default only on the localhost.
    fun autoDiscoverDefaultIP(): InetAddress? {
        autoDiscoveredDefaultIP =
            Optional.ofNullable(autoDiscoveredDefaultIP).orElseGet { InetAddress.getLoopbackAddress() }

        return autoDiscoveredDefaultIP
    }

    @CommandLine.Option(
        names = ["--net-restrict"],
        arity = "1..*",
        split = ",",
        converter = [SubnetInfoConverter::class],
        description = ["Comma-separated list of allowed IP subnets (e.g., '192.168.1.0/24,10.0.0.0/8')."]
    )
    private var allowedSubnets: List<SubnetInfo>? = null

    override fun toDomainObject(): P2PDiscoveryConfiguration {
        return P2PDiscoveryConfiguration(
            p2pEnabled,
            peerDiscoveryEnabled,
            p2pHost,
            p2pInterface,
            p2pPort,
            maxPeers,
            isLimitRemoteWireConnectionsEnabled,
            maxRemoteConnectionsPercentage,
            randomPeerPriority,
            bannedNodeIds,
            allowedSubnets,
            poaDiscoveryRetryBootnodes,
            bootNodes,
            discoveryDnsUrl
        )
    }

    /**
     * Validates the provided P2P discovery options.
     *
     * @param commandLine The command line object used for parsing and error reporting.
     * @param networkInterfaceChecker The checker used to validate the network interface.
     */
    fun validate(
        commandLine: CommandLine, networkInterfaceChecker: NetworkInterfaceChecker
    ) {
        validateP2PHost(commandLine)
        validateP2PInterface(commandLine, networkInterfaceChecker)
    }

    private fun validateP2PInterface(
        commandLine: CommandLine, networkInterfaceChecker: NetworkInterfaceChecker
    ) {
        val failMessage = "The provided --p2p-interface is not available: $p2pInterface"
        try {
            if (!networkInterfaceChecker.isNetworkInterfaceAvailable(p2pInterface)) {
                throw CommandLine.ParameterException(commandLine, failMessage)
            }
        } catch (e: UnknownHostException) {
            throw CommandLine.ParameterException(commandLine, failMessage, e)
        } catch (e: SocketException) {
            throw CommandLine.ParameterException(commandLine, failMessage, e)
        }
    }

    private fun validateP2PHost(commandLine: CommandLine) {
        val failMessage = "The provided --p2p-host is invalid: $p2pHost"
        if (!InetAddresses.isInetAddress(p2pHost)) {
            throw CommandLine.ParameterException(commandLine, failMessage)
        }
    }

    override fun getCLIOptions(): List<String> {
        return CommandLineUtils.getCLIOptions(this, P2PDiscoveryOptions())
    }
}
