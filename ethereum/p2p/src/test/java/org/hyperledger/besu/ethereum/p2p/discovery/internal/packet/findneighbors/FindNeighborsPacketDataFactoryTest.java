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
package org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.findneighbors;

import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.validation.ExpiryValidator;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.validation.TargetValidator;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FindNeighborsPacketDataFactoryTest {
  private @Mock TargetValidator targetValidator;
  private @Mock ExpiryValidator expiryValidator;
  private final Clock clock = Clock.fixed(Instant.ofEpochSecond(123), ZoneId.of("UTC"));

  private FindNeighborsPacketDataFactory factory;

  @BeforeEach
  public void beforeTest() {
    factory = new FindNeighborsPacketDataFactory(targetValidator, expiryValidator, clock);
  }

  @Test
  public void testCreateWithExpiry() {
    final Bytes target = Mockito.mock(Bytes.class);
    final long expiration = 456;

    FindNeighborsPacketData findNeighborsPacketData = factory.create(target, expiration);

    Mockito.verify(targetValidator).validate(target);
    Mockito.verify(expiryValidator).validate(expiration);

    Assertions.assertEquals(target, findNeighborsPacketData.getTarget());
    Assertions.assertEquals(expiration, findNeighborsPacketData.getExpiration());
  }

  @Test
  public void testCreateWithExpiryAndInvalidTarget() {
    final Bytes target = Mockito.mock(Bytes.class);
    final long expiration = 456;

    Mockito.doThrow(new IllegalArgumentException()).when(targetValidator).validate(target);

    Assertions.assertThrows(
        IllegalArgumentException.class, () -> factory.create(target, expiration));

    Mockito.verify(targetValidator).validate(target);
    Mockito.verifyNoInteractions(expiryValidator);
  }

  @Test
  public void testCreateWithInvalidExpiry() {
    final Bytes target = Mockito.mock(Bytes.class);
    final long expiration = 456;

    Mockito.doThrow(new IllegalArgumentException()).when(expiryValidator).validate(expiration);

    Assertions.assertThrows(
        IllegalArgumentException.class, () -> factory.create(target, expiration));

    Mockito.verify(targetValidator).validate(target);
    Mockito.verify(expiryValidator).validate(expiration);
  }

  @Test
  public void testCreateWithoutExpiry() {
    final Bytes target = Mockito.mock(Bytes.class);

    FindNeighborsPacketData findNeighborsPacketData = factory.create(target);

    Mockito.verify(targetValidator).validate(target);
    Mockito.verifyNoInteractions(expiryValidator);

    Assertions.assertEquals(target, findNeighborsPacketData.getTarget());
    Assertions.assertEquals(
        clock.instant().getEpochSecond() + 60, findNeighborsPacketData.getExpiration());
  }

  @Test
  public void testCreateWithoutExpiryAndInvalidTarget() {
    final Bytes target = Mockito.mock(Bytes.class);

    Mockito.doThrow(new IllegalArgumentException()).when(targetValidator).validate(target);

    Assertions.assertThrows(IllegalArgumentException.class, () -> factory.create(target));

    Mockito.verify(targetValidator).validate(target);
    Mockito.verifyNoInteractions(expiryValidator);
  }
}
