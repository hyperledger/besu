/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.util;

import tech.pegasys.pantheon.cli.EthNetworkConfig;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.peers.Endpoint;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import org.bouncycastle.util.encoders.Hex;

public class PermissioningConfigurationValidator {

  public static void areAllBootnodesAreInWhitelist(
      final EthNetworkConfig ethNetworkConfig,
      final PermissioningConfiguration permissioningConfiguration)
      throws Exception {
    List<Peer> bootnodesNotInWhitelist = new ArrayList<>();
    final List<Peer> bootnodes =
        DiscoveryConfiguration.getBootstrapPeersFromGenericCollection(
            ethNetworkConfig.getBootNodes());
    if (permissioningConfiguration.isNodeWhitelistSet() && bootnodes != null) {
      bootnodesNotInWhitelist =
          bootnodes
              .stream()
              .filter(
                  node ->
                      !permissioningConfiguration
                          .getNodeWhitelist()
                          .contains(URI.create(buildEnodeURI(node))))
              .collect(Collectors.toList());
    }
    if (!bootnodesNotInWhitelist.isEmpty()) {
      throw new Exception("Bootnode(s) not in nodes-whitelist " + bootnodesNotInWhitelist);
    }
  }

  private static String buildEnodeURI(final Peer s) {
    String url = Hex.toHexString(s.getId().extractArray());
    Endpoint endpoint = s.getEndpoint();
    String nodeIp = endpoint.getHost();
    OptionalInt tcpPort = endpoint.getTcpPort();
    int udpPort = endpoint.getUdpPort();

    if (tcpPort.isPresent() && (tcpPort.getAsInt() != udpPort)) {
      return String.format(
          "enode://%s@%s:%d?discport=%d", url, nodeIp, tcpPort.getAsInt(), udpPort);
    } else {
      return String.format("enode://%s@%s:%d", url, nodeIp, udpPort);
    }
  }
}
