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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.pong;

import org.hyperledger.besu.ethereum.p2p.discovery.Endpoint;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.validation.ExpiryValidator;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PongPacketDataFactoryTest {
  private @Mock ExpiryValidator expiryValidator;
  private final Clock clock = Clock.fixed(Instant.ofEpochSecond(123), ZoneId.of("UTC"));

  private PongPacketDataFactory factory;

  @BeforeEach
  public void beforeTest() {
    factory = new PongPacketDataFactory(expiryValidator, clock);
  }

  @Test
  public void testCreateWithExpiry() {
    final Endpoint to = Mockito.mock(Endpoint.class);
    final Bytes pingHash = Mockito.mock(Bytes.class);
    final long expiration = 456;
    final UInt64 enrSeq = UInt64.MAX_VALUE;

    PongPacketData pongPacketData = factory.create(to, pingHash, expiration, enrSeq);

    Mockito.verify(expiryValidator).validate(expiration);

    Assertions.assertEquals(to, pongPacketData.getTo());
    Assertions.assertEquals(pingHash, pongPacketData.getPingHash());
    Assertions.assertEquals(expiration, pongPacketData.getExpiration());
    Assertions.assertTrue(pongPacketData.getEnrSeq().isPresent());
    Assertions.assertEquals(enrSeq, pongPacketData.getEnrSeq().get());
  }

  @Test
  public void testCreateWithInvalidExpiry() {
    final Endpoint to = Mockito.mock(Endpoint.class);
    final Bytes pingHash = Mockito.mock(Bytes.class);
    final long expiration = 456;
    final UInt64 enrSeq = UInt64.MAX_VALUE;

    Mockito.doThrow(IllegalArgumentException.class).when(expiryValidator).validate(expiration);

    Assertions.assertThrows(
        IllegalArgumentException.class, () -> factory.create(to, pingHash, expiration, enrSeq));

    Mockito.verify(expiryValidator).validate(expiration);
  }

  @Test
  public void testCreateWithoutExpiry() {
    final Endpoint to = Mockito.mock(Endpoint.class);
    final Bytes pingHash = Mockito.mock(Bytes.class);
    final UInt64 enrSeq = UInt64.MAX_VALUE;

    PongPacketData pongPacketData = factory.create(to, pingHash, enrSeq);

    Mockito.verifyNoInteractions(expiryValidator);

    Assertions.assertEquals(to, pongPacketData.getTo());
    Assertions.assertEquals(pingHash, pongPacketData.getPingHash());
    Assertions.assertEquals(clock.instant().getEpochSecond() + 60, pongPacketData.getExpiration());
    Assertions.assertTrue(pongPacketData.getEnrSeq().isPresent());
    Assertions.assertEquals(enrSeq, pongPacketData.getEnrSeq().get());
  }
}
