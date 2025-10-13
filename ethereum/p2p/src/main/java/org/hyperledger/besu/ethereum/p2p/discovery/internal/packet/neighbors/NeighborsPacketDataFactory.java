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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.neighbors;

import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.PacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.validation.DiscoveryPeersValidator;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.validation.ExpiryValidator;

import java.time.Clock;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NeighborsPacketDataFactory {
  private final DiscoveryPeersValidator discoveryPeersValidator;
  private final ExpiryValidator expiryValidator;
  private final Clock clock;

  public @Inject NeighborsPacketDataFactory(
      final DiscoveryPeersValidator discoveryPeersValidator,
      final ExpiryValidator expiryValidator,
      final Clock clock) {
    this.discoveryPeersValidator = discoveryPeersValidator;
    this.expiryValidator = expiryValidator;
    this.clock = clock;
  }

  public NeighborsPacketData create(final List<DiscoveryPeer> peers, final long expiration) {
    discoveryPeersValidator.validate(peers);
    expiryValidator.validate(expiration);
    return new NeighborsPacketData(peers, expiration);
  }

  public NeighborsPacketData create(final List<DiscoveryPeer> peers) {
    discoveryPeersValidator.validate(peers);
    return new NeighborsPacketData(peers, getDefaultExpirationTime());
  }

  private long getDefaultExpirationTime() {
    return clock.instant().getEpochSecond() + PacketData.DEFAULT_EXPIRATION_PERIOD_SEC;
  }
}
