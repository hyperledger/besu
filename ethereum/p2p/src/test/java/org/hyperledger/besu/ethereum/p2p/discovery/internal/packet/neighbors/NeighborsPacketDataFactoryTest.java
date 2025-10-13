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
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.validation.DiscoveryPeersValidator;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.validation.ExpiryValidator;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NeighborsPacketDataFactoryTest {
  private @Mock DiscoveryPeersValidator discoveryPeersValidator;
  private @Mock ExpiryValidator expiryValidator;
  private final Clock clock = Clock.fixed(Instant.ofEpochSecond(123), ZoneId.of("UTC"));

  private NeighborsPacketDataFactory factory;

  @BeforeEach
  public void beforeTest() {
    factory = new NeighborsPacketDataFactory(discoveryPeersValidator, expiryValidator, clock);
  }

  @Test
  public void testCreateWithExpiry() {
    final List<DiscoveryPeer> peers = List.of(Mockito.mock(DiscoveryPeer.class));
    final long expiration = 456;

    NeighborsPacketData neighborsPacketData = factory.create(peers, expiration);

    Mockito.verify(discoveryPeersValidator).validate(peers);
    Mockito.verify(expiryValidator).validate(expiration);

    Assertions.assertEquals(peers, neighborsPacketData.getNodes());
    Assertions.assertEquals(expiration, neighborsPacketData.getExpiration());
  }

  @Test
  public void testCreateWithExpiryAndInvalidPeers() {
    final List<DiscoveryPeer> peers = List.of(Mockito.mock(DiscoveryPeer.class));
    final long expiration = 456;

    Mockito.doThrow(new IllegalArgumentException()).when(discoveryPeersValidator).validate(peers);

    Assertions.assertThrows(
        IllegalArgumentException.class, () -> factory.create(peers, expiration));

    Mockito.verify(discoveryPeersValidator).validate(peers);
    Mockito.verifyNoInteractions(expiryValidator);
  }

  @Test
  public void testCreateWithInvalidExpiry() {
    final List<DiscoveryPeer> peers = List.of(Mockito.mock(DiscoveryPeer.class));
    final long expiration = 456;

    Mockito.doThrow(new IllegalArgumentException()).when(expiryValidator).validate(expiration);

    Assertions.assertThrows(
        IllegalArgumentException.class, () -> factory.create(peers, expiration));

    Mockito.verify(discoveryPeersValidator).validate(peers);
    Mockito.verify(expiryValidator).validate(expiration);
  }

  @Test
  public void testCreateWithoutExpiry() {
    final List<DiscoveryPeer> peers = List.of(Mockito.mock(DiscoveryPeer.class));

    NeighborsPacketData neighborsPacketData = factory.create(peers);

    Mockito.verify(discoveryPeersValidator).validate(peers);
    Mockito.verifyNoInteractions(expiryValidator);

    Assertions.assertEquals(peers, neighborsPacketData.getNodes());
    Assertions.assertEquals(
        clock.instant().getEpochSecond() + 60, neighborsPacketData.getExpiration());
  }

  @Test
  public void testCreateWithoutExpiryWithInvalidPeers() {
    final List<DiscoveryPeer> peers = List.of(Mockito.mock(DiscoveryPeer.class));

    Mockito.doThrow(new IllegalArgumentException()).when(discoveryPeersValidator).validate(peers);

    Assertions.assertThrows(IllegalArgumentException.class, () -> factory.create(peers));

    Mockito.verify(discoveryPeersValidator).validate(peers);
    Mockito.verifyNoInteractions(expiryValidator);
  }
}
