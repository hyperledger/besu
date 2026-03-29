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
package org.hyperledger.besu.ethereum.p2p.discovery.discv4.internal;

import java.util.Locale;

/** The status of a {@link DiscoveryPeerV4}, in relation to the peer discovery state machine. */
enum PeerDiscoveryStatus {

  /**
   * Represents a newly discovered {@link DiscoveryPeerV4}, prior to commencing the bonding
   * exchange.
   */
  KNOWN,

  /**
   * Bonding with this peer is in progress. If we're unable to establish communication and/or
   * complete the bonding exchange, the {@link DiscoveryPeerV4} remains in this state, until we
   * ultimately desist.
   */
  BONDING,

  /**
   * We have successfully bonded with this {@link DiscoveryPeerV4}, and we are able to exchange
   * messages with them.
   */
  BONDED;

  @Override
  public String toString() {
    return name().toLowerCase(Locale.ROOT);
  }
}
