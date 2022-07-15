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
package org.hyperledger.besu.cli.options.unstable;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.cli.options.OptionParser;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import picocli.CommandLine;

public class NetworkingOptions implements CLIOptions<NetworkingConfiguration> {
  private final String INITIATE_CONNECTIONS_FREQUENCY_FLAG =
      "--Xp2p-initiate-connections-frequency";
  private final String CHECK_MAINTAINED_CONNECTIONS_FREQUENCY_FLAG =
      "--Xp2p-check-maintained-connections-frequency";
  private final String DNS_DISCOVERY_SERVER_OVERRIDE_FLAG = "--Xp2p-dns-discovery-server";
  private final String DISCOVERY_PROTOCOL_V5_ENABLED = "--Xv5-discovery-enabled";

  @CommandLine.Option(
      names = INITIATE_CONNECTIONS_FREQUENCY_FLAG,
      hidden = true,
      defaultValue = "30",
      paramLabel = "<INTEGER>",
      description =
          "The frequency (in seconds) at which to initiate new outgoing connections (default: ${DEFAULT-VALUE})")
  private int initiateConnectionsFrequencySec =
      NetworkingConfiguration.DEFAULT_INITIATE_CONNECTIONS_FREQUENCY_SEC;

  @CommandLine.Option(
      names = CHECK_MAINTAINED_CONNECTIONS_FREQUENCY_FLAG,
      hidden = true,
      defaultValue = "60",
      paramLabel = "<INTEGER>",
      description =
          "The frequency (in seconds) at which to check maintained connections (default: ${DEFAULT-VALUE})")
  private int checkMaintainedConnectionsFrequencySec =
      NetworkingConfiguration.DEFAULT_CHECK_MAINTAINED_CONNECTIONS_FREQUENCY_SEC;

  @CommandLine.Option(
      names = DNS_DISCOVERY_SERVER_OVERRIDE_FLAG,
      hidden = true,
      description =
          "DNS server host to use for doing DNS Discovery of peers, rather than the machine's configured DNS server")
  private Optional<String> dnsDiscoveryServerOverride = Optional.empty();

  @CommandLine.Option(
      names = DISCOVERY_PROTOCOL_V5_ENABLED,
      hidden = true,
      defaultValue = "false",
      description = "Whether to enable P2P Discovery Protocol v5 (default: ${DEFAULT-VALUE})")
  private final Boolean isPeerDiscoveryV5Enabled = false;

  private NetworkingOptions() {}

  public static NetworkingOptions create() {
    return new NetworkingOptions();
  }

  public static NetworkingOptions fromConfig(final NetworkingConfiguration networkingConfig) {
    final NetworkingOptions cliOptions = new NetworkingOptions();
    cliOptions.checkMaintainedConnectionsFrequencySec =
        networkingConfig.getCheckMaintainedConnectionsFrequencySec();
    cliOptions.initiateConnectionsFrequencySec =
        networkingConfig.getInitiateConnectionsFrequencySec();
    cliOptions.dnsDiscoveryServerOverride = networkingConfig.getDnsDiscoveryServerOverride();

    return cliOptions;
  }

  @Override
  public NetworkingConfiguration toDomainObject() {
    NetworkingConfiguration config = NetworkingConfiguration.create();
    config.setCheckMaintainedConnectionsFrequency(checkMaintainedConnectionsFrequencySec);
    config.setInitiateConnectionsFrequency(initiateConnectionsFrequencySec);
    config.setDnsDiscoveryServerOverride(dnsDiscoveryServerOverride);
    config.getDiscovery().setDiscoveryV5Enabled(isPeerDiscoveryV5Enabled);
    return config;
  }

  @Override
  public List<String> getCLIOptions() {
    List<String> retval =
        Arrays.asList(
            CHECK_MAINTAINED_CONNECTIONS_FREQUENCY_FLAG,
            OptionParser.format(checkMaintainedConnectionsFrequencySec),
            INITIATE_CONNECTIONS_FREQUENCY_FLAG,
            OptionParser.format(initiateConnectionsFrequencySec));

    if (dnsDiscoveryServerOverride.isPresent()) {
      retval.add(DNS_DISCOVERY_SERVER_OVERRIDE_FLAG);
      retval.add(dnsDiscoveryServerOverride.get());
    }
    return retval;
  }
}
