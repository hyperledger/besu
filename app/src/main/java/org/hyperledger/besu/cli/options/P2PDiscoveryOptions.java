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
package org.hyperledger.besu.cli.options;

import org.hyperledger.besu.cli.DefaultCommandValues;
import org.hyperledger.besu.cli.converter.PercentageConverter;
import org.hyperledger.besu.cli.converter.SubnetInfoConverter;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.ethereum.p2p.discovery.P2PDiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.util.NetworkUtility;
import org.hyperledger.besu.util.number.Fraction;
import org.hyperledger.besu.util.number.Percentage;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.net.InetAddresses;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine;

/** Command line options for configuring P2P discovery on the node. */
public class P2PDiscoveryOptions implements CLIOptions<P2PDiscoveryConfiguration> {

  /** Functional interface for checking if a network interface is available. */
  @FunctionalInterface
  public interface NetworkInterfaceChecker {

    /**
     * Checks if the provided network interface is available.
     *
     * @param p2pInterface The network interface to check.
     * @return True if the network interface is available, false otherwise.
     * @throws UnknownHostException If the host is unknown.
     * @throws SocketException If there is an error with the socket.
     */
    boolean isNetworkInterfaceAvailable(final String p2pInterface)
        throws UnknownHostException, SocketException;
  }

  /** Default constructor */
  public P2PDiscoveryOptions() {}

  // Public IP stored to prevent having to research it each time we need it.
  private InetAddress autoDiscoveredDefaultIP = null;

  /** Completely disables P2P within Besu. */
  @CommandLine.Option(
      names = {"--p2p-enabled"},
      description = "Enable P2P functionality (default: ${DEFAULT-VALUE})",
      arity = "1")
  public final Boolean p2pEnabled = true;

  /** Boolean option to indicate if peers should be discovered. */
  @CommandLine.Option(
      names = {"--discovery-enabled"},
      description = "Enable P2P discovery (default: ${DEFAULT-VALUE})",
      arity = "1")
  public final Boolean peerDiscoveryEnabled = true;

  /**
   * A list of bootstrap nodes can be passed and a hardcoded list will be used otherwise by the
   * Runner.
   */
  // NOTE: we have no control over default value here.
  @CommandLine.Option(
      names = {"--bootnodes"},
      paramLabel = "<enode://id@host:port>",
      description =
          "Comma separated enode URLs for P2P discovery bootstrap. "
              + "Default is a predefined list.",
      split = ",",
      arity = "0..*")
  public final List<String> bootNodes = null;

  /** The IP the node advertises to peers for P2P communication. */
  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--p2p-host"},
      paramLabel = DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP,
      description = "IP address this node advertises to its peers (default: ${DEFAULT-VALUE})",
      arity = "1")
  public String p2pHost = autoDiscoverDefaultIP().getHostAddress();

  /** The network interface address on which this node listens for P2P communication. */
  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--p2p-interface"},
      paramLabel = DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP,
      description =
          "The network interface address on which this node listens for P2P communication (default: ${DEFAULT-VALUE})",
      arity = "1")
  public String p2pInterface = NetworkUtility.INADDR_ANY;

  /** The port on which this node listens for P2P communication. */
  @CommandLine.Option(
      names = {"--p2p-port"},
      paramLabel = DefaultCommandValues.MANDATORY_PORT_FORMAT_HELP,
      description = "Port on which to listen for P2P communication (default: ${DEFAULT-VALUE})",
      arity = "1")
  public final Integer p2pPort = EnodeURLImpl.DEFAULT_LISTENING_PORT;

  /** The maximum number of peers this node can connect to. */
  @CommandLine.Option(
      names = {"--max-peers", "--p2p-peer-upper-bound"},
      paramLabel = DefaultCommandValues.MANDATORY_INTEGER_FORMAT_HELP,
      description = "Maximum P2P connections that can be established (default: ${DEFAULT-VALUE})")
  public final Integer maxPeers = DefaultCommandValues.DEFAULT_MAX_PEERS;

  /** Boolean option to limit the number of P2P connections initiated remotely. */
  @CommandLine.Option(
      names = {"--remote-connections-limit-enabled"},
      description =
          "Whether to limit the number of P2P connections initiated remotely. (default: ${DEFAULT-VALUE})")
  public final Boolean isLimitRemoteWireConnectionsEnabled = true;

  /** The maximum percentage of P2P connections that can be initiated remotely. */
  @CommandLine.Option(
      names = {"--remote-connections-max-percentage"},
      paramLabel = DefaultCommandValues.MANDATORY_DOUBLE_FORMAT_HELP,
      description =
          "The maximum percentage of P2P connections that can be initiated remotely. Must be between 0 and 100 inclusive. (default: ${DEFAULT-VALUE})",
      arity = "1",
      converter = PercentageConverter.class)
  public final Percentage maxRemoteConnectionsPercentage =
      Fraction.fromFloat(DefaultCommandValues.DEFAULT_FRACTION_REMOTE_WIRE_CONNECTIONS_ALLOWED)
          .toPercentage();

  /** The URL to use for DNS discovery. */
  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--discovery-dns-url"},
      description = "Specifies the URL to use for DNS discovery")
  public String discoveryDnsUrl = null;

  /** Boolean option to allow for incoming connections to be prioritized randomly. */
  @CommandLine.Option(
      names = {"--random-peer-priority-enabled"},
      description =
          "Allow for incoming connections to be prioritized randomly. This will prevent (typically small, stable) networks from forming impenetrable peer cliques. (default: ${DEFAULT-VALUE})")
  public final Boolean randomPeerPriority = Boolean.FALSE;

  /** A list of node IDs to ban from the P2P network. */
  @CommandLine.Option(
      names = {"--banned-node-ids", "--banned-node-id"},
      paramLabel = DefaultCommandValues.MANDATORY_NODE_ID_FORMAT_HELP,
      description = "A list of node IDs to ban from the P2P network.",
      split = ",",
      arity = "1..*")
  void setBannedNodeIds(final List<String> values) {
    try {
      bannedNodeIds =
          values.stream()
              .filter(value -> !value.isEmpty())
              .map(EnodeURLImpl::parseNodeId)
              .collect(Collectors.toList());
    } catch (final IllegalArgumentException e) {
      throw new CommandLine.ParameterException(
          new CommandLine(this), "Invalid ids supplied to '--banned-node-ids'. " + e.getMessage());
    }
  }

  // Boolean option to set that in a PoA network the bootnodes should always be queried during
  // peer table refresh. If this flag is disabled bootnodes are only sent FINDN requests on first
  // startup, meaning that an offline bootnode or network outage at the client can prevent it
  // discovering any peers without a restart.
  @CommandLine.Option(
      names = {"--poa-discovery-retry-bootnodes"},
      description =
          "Always use of bootnodes for discovery in PoA networks. Disabling this reverts "
              + " to the same behaviour as non-PoA networks, where neighbours are only discovered from bootnodes on first startup."
              + "(default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Boolean poaDiscoveryRetryBootnodes = true;

  private Collection<Bytes> bannedNodeIds = new ArrayList<>();

  /**
   * Auto-discovers the default IP of the client.
   *
   * @return machine loopback address
   */
  // Loopback IP is used by default as this is how smokeTests require it to be
  // and it's probably a good security behaviour to default only on the localhost.
  public InetAddress autoDiscoverDefaultIP() {
    autoDiscoveredDefaultIP =
        Optional.ofNullable(autoDiscoveredDefaultIP).orElseGet(InetAddress::getLoopbackAddress);

    return autoDiscoveredDefaultIP;
  }

  @CommandLine.Option(
      names = {"--net-restrict"},
      arity = "1..*",
      split = ",",
      converter = SubnetInfoConverter.class,
      description =
          "Comma-separated list of allowed IP subnets (e.g., '192.168.1.0/24,10.0.0.0/8').")
  private List<SubnetUtils.SubnetInfo> allowedSubnets;

  @Override
  public P2PDiscoveryConfiguration toDomainObject() {
    return new P2PDiscoveryConfiguration(
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
        discoveryDnsUrl);
  }

  /**
   * Validates the provided P2P discovery options.
   *
   * @param commandLine The command line object used for parsing and error reporting.
   * @param networkInterfaceChecker The checker used to validate the network interface.
   */
  public void validate(
      final CommandLine commandLine, final NetworkInterfaceChecker networkInterfaceChecker) {
    validateP2PHost(commandLine);
    validateP2PInterface(commandLine, networkInterfaceChecker);
  }

  private void validateP2PInterface(
      final CommandLine commandLine, final NetworkInterfaceChecker networkInterfaceChecker) {
    final String failMessage = "The provided --p2p-interface is not available: " + p2pInterface;
    try {
      if (!networkInterfaceChecker.isNetworkInterfaceAvailable(p2pInterface)) {
        throw new CommandLine.ParameterException(commandLine, failMessage);
      }
    } catch (final UnknownHostException | SocketException e) {
      throw new CommandLine.ParameterException(commandLine, failMessage, e);
    }
  }

  private void validateP2PHost(final CommandLine commandLine) {
    final String failMessage = "The provided --p2p-host is invalid: " + p2pHost;
    if (!InetAddresses.isInetAddress(p2pHost)) {
      throw new CommandLine.ParameterException(commandLine, failMessage);
    }
  }

  @Override
  public List<String> getCLIOptions() {
    return CommandLineUtils.getCLIOptions(this, new P2PDiscoveryOptions());
  }
}
