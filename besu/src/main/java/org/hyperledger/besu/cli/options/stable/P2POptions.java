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
package org.hyperledger.besu.cli.options.stable;

import static java.util.Arrays.asList;
import static org.hyperledger.besu.cli.options.unstable.NetworkingOptions.PEER_LOWER_BOUND_FLAG;
import static org.hyperledger.besu.cli.util.CommandLineUtils.isOptionSet;
import static org.hyperledger.besu.ethereum.p2p.config.AutoDiscoverDefaultIP.autoDiscoverDefaultIP;

import org.hyperledger.besu.cli.DefaultCommandValues;
import org.hyperledger.besu.cli.converter.PercentageConverter;
import org.hyperledger.besu.cli.converter.SubnetInfoConverter;
import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.ethereum.p2p.config.P2PConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.util.NetworkUtility;
import org.hyperledger.besu.util.number.Fraction;
import org.hyperledger.besu.util.number.Percentage;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import picocli.CommandLine;

/** The P2POptions Config Cli Options. */
public class P2POptions implements CLIOptions<P2PConfiguration> {

  // Completely disables P2P within Besu.
  @CommandLine.Option(
      names = {"--p2p-enabled"},
      description = "Enable P2P functionality (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Boolean p2pEnabled = true;

  // Boolean option to indicate if peers should NOT be discovered, default to
  // false indicates that
  // the peers should be discovered by default.
  //
  // This negative option is required because of the nature of the option that is
  // true when
  // added on the command line. You can't do --option=false, so false is set as
  // default
  // and you have not to set the option at all if you want it false.
  // This seems to be the only way it works with Picocli.
  // Also many other software use the same negative option scheme for false
  // defaults
  // meaning that it's probably the right way to handle disabling options.
  @CommandLine.Option(
      names = {"--discovery-enabled"},
      description = "Enable P2P discovery (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Boolean peerDiscoveryEnabled = true;

  // A list of bootstrap nodes can be passed
  // and a hardcoded list will be used otherwise by the Runner.
  // NOTE: we have no control over default value here.
  @CommandLine.Option(
      names = {"--bootnodes"},
      paramLabel = "<enode://id@host:port>",
      description =
          "Comma separated enode URLs for P2P discovery bootstrap. "
              + "Default is a predefined list.",
      split = ",",
      arity = "0..*")
  private final List<String> bootNodes = null;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--p2p-host"},
      paramLabel = DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP,
      description = "IP address this node advertises to its peers (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String p2pHost = autoDiscoverDefaultIP().getHostAddress();

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--p2p-interface"},
      paramLabel = DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP,
      description =
          "The network interface address on which this node listens for P2P communication (default: ${DEFAULT-VALUE})",
      arity = "1")
  private String p2pInterface = NetworkUtility.INADDR_ANY;

  @CommandLine.Option(
      names = {"--p2p-port"},
      paramLabel = DefaultCommandValues.MANDATORY_PORT_FORMAT_HELP,
      description = "Port on which to listen for P2P communication (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer p2pPort = EnodeURLImpl.DEFAULT_LISTENING_PORT;

  @CommandLine.Option(
      names = {"--max-peers", "--p2p-peer-upper-bound"},
      paramLabel = DefaultCommandValues.MANDATORY_INTEGER_FORMAT_HELP,
      description = "Maximum P2P connections that can be established (default: ${DEFAULT-VALUE})")
  private final Integer maxPeers = DefaultCommandValues.DEFAULT_MAX_PEERS;

  @CommandLine.Option(
      names = {"--remote-connections-limit-enabled"},
      description =
          "Whether to limit the number of P2P connections initiated remotely. (default: ${DEFAULT-VALUE})")
  private final Boolean isLimitRemoteWireConnectionsEnabled = true;

  @CommandLine.Option(
      names = {"--remote-connections-max-percentage"},
      paramLabel = DefaultCommandValues.MANDATORY_DOUBLE_FORMAT_HELP,
      description =
          "The maximum percentage of P2P connections that can be initiated remotely. Must be between 0 and 100 inclusive. (default: ${DEFAULT-VALUE})",
      arity = "1",
      converter = PercentageConverter.class)
  private final Percentage maxRemoteConnectionsPercentage =
      Fraction.fromFloat(DefaultCommandValues.DEFAULT_FRACTION_REMOTE_WIRE_CONNECTIONS_ALLOWED)
          .toPercentage();

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--discovery-dns-url"},
      description = "Specifies the URL to use for DNS discovery")
  private String discoveryDnsUrl = null;

  @CommandLine.Option(
      names = {"--random-peer-priority-enabled"},
      description =
          "Allow for incoming connections to be prioritized randomly. This will prevent (typically small, stable) networks from forming impenetrable peer cliques. (default: ${DEFAULT-VALUE})")
  private final Boolean randomPeerPriority = Boolean.FALSE;

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

  @CommandLine.Option(
      names = {"--net-restrict"},
      arity = "1..*",
      split = ",",
      converter = SubnetInfoConverter.class,
      description =
          "Comma-separated list of allowed IP subnets (e.g., '192.168.1.0/24,10.0.0.0/8').")
  private List<SubnetInfo> allowedSubnets;

  private List<Bytes> bannedNodeIds = new ArrayList<>();

  /** Default constructor. */
  public P2POptions() {}

  /**
   * Validates P2P options
   *
   * @param commandLine the commandLine
   * @param logger the logger
   */
  public void validate(final CommandLine commandLine, final Logger logger) {
    final float fraction = Fraction.fromPercentage(maxRemoteConnectionsPercentage).getValue();
    if (!(fraction >= 0.0 && fraction <= 1.0)) {
      throw new CommandLine.ParameterException(
          commandLine,
          "Fraction of remote connections allowed must be between 0.0 and 1.0 (inclusive).");
    }
    checkP2pOptionsDependencies(commandLine, logger);
  }

  /**
   * Validates P2P interface IP address/host name. Visible for testing.
   *
   * @param commandLine the commandLine
   * @param logger the logger
   */
  public void validateP2PInterface(final CommandLine commandLine, final Logger logger) {
    final String failMessage = "The provided --p2p-interface is not available: " + p2pInterface;
    try {
      if (!NetworkUtility.isNetworkInterfaceAvailable(p2pInterface)) {
        throw new CommandLine.ParameterException(commandLine, failMessage);
      }
    } catch (final UnknownHostException | SocketException e) {
      throw new CommandLine.ParameterException(commandLine, failMessage, e);
    }
    ensureValidPeerBoundParams(commandLine, logger);
  }

  private void ensureValidPeerBoundParams(final CommandLine commandLine, final Logger logger) {
    if (isOptionSet(commandLine, PEER_LOWER_BOUND_FLAG)) {
      logger.warn(PEER_LOWER_BOUND_FLAG + " is deprecated and will be removed soon.");
    }
  }

  private void checkP2pOptionsDependencies(final CommandLine commandLine, final Logger logger) {
    // Check that P2P options are able to work
    CommandLineUtils.checkOptionDependencies(
        logger,
        commandLine,
        "--p2p-enabled",
        !p2pEnabled,
        asList(
            "--bootnodes",
            "--discovery-enabled",
            "--max-peers",
            "--banned-node-id",
            "--banned-node-ids",
            "--p2p-host",
            "--p2p-interface",
            "--p2p-port",
            "--remote-connections-max-percentage"));
  }

  @Override
  public P2PConfiguration toDomainObject() {
    return new P2PConfiguration.Builder()
        .p2pEnabled(p2pEnabled)
        .discoveryEnabled(peerDiscoveryEnabled)
        .bootNodes(bootNodes)
        .host(p2pHost)
        .p2pInterface(p2pInterface)
        .port(p2pPort)
        .maxPeers(maxPeers)
        .isLimitRemoteWireConnectionsEnabled(isLimitRemoteWireConnectionsEnabled)
        .maxRemoteConnectionsPercentage(maxRemoteConnectionsPercentage)
        .discoveryDnsUrl(discoveryDnsUrl)
        .randomPeerPriority(randomPeerPriority)
        .bannedNodeIds(bannedNodeIds)
        .allowedSubnets(allowedSubnets)
        .build();
  }

  @Override
  public List<String> getCLIOptions() {
    return CommandLineUtils.getCLIOptions(this, new P2POptions());
  }
}
