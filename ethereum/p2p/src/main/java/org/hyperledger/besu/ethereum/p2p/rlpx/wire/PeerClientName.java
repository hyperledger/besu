/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.p2p.rlpx.wire;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

public enum PeerClientName {
  BESU("besu"),
  ERIGON("erigon"),
  GETH("Geth"),
  NETHERMIND("Nethermind"),
  NIMBUS("nimbus-eth1"),
  RETH("reth"),
  UNKNOWN(null);

  private final String agentName;
  private final String displayName;

  PeerClientName(final String agentName) {
    this.agentName = agentName;
    this.displayName = StringUtils.capitalize(name().toLowerCase(Locale.ENGLISH));
  }

  public String getDisplayName() {
    return displayName;
  }

  public static PeerClientName fromAgentName(final String agentName) {
    for (final var type : PeerClientName.values()) {
      if (type != UNKNOWN && type.agentName.equals(agentName)) {
        return type;
      }
    }
    return UNKNOWN;
  }
}
