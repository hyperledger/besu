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

package org.hyperledger.besu.cli.subcommands.networkcreate.generate;

import java.util.Arrays;
import java.util.List;

/**
 * Port configuration and utility to prevent port collision
 *
 * <p>Based on ports groups and their length to increment the port numbers for each node without
 * using arbitrary ranges and taking the node position in siblings list in account.
 */
public enum PortConfig {
  P2P("p2p-port", 30303, Group.P2P),
  RPC_HTTP("rpc-http-port", 8545, Group.RPC),
  RPC_WS("rpc-ws-port", 8546, Group.RPC),
  GRAPHQL("graphql-http-port", 8547, Group.RPC),
  METRICS("metrics-port", 9545, Group.METRICS);

  // Port group.
  // All ports in the same group will be incremented together.
  private enum Group {
    P2P,
    RPC,
    METRICS;
  }

  private final String key;
  private final Integer basePort;
  private final Group group;

  PortConfig(final String key, final Integer basePort, final Group group) {
    this.key = key;
    this.basePort = basePort;
    this.group = group;
  }

  public String getKey() {
    return key;
  }

  /**
   * generates a port number based on the current enum default and group and the node position.
   *
   * @param siblings list of objects (usually nodes) to base the port increment on
   * @param current current object in the siblings list
   * @return the port number
   */
  public Integer getPort(final List<?> siblings, final Object current) {
    final int positionInNodesList = siblings.indexOf(current);
    final int groupSize =
        (int)
            Arrays.stream(PortConfig.values())
                .filter(value -> value.group.equals(this.group))
                .count();
    // ensure no port collision on this network
    return basePort + positionInNodesList * groupSize;
  }
}
