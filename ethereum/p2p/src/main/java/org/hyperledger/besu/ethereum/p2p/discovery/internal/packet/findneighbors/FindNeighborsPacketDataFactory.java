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

import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.PacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.validation.ExpiryValidator;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.validation.TargetValidator;

import java.time.Clock;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.tuweni.bytes.Bytes;

@Singleton
public class FindNeighborsPacketDataFactory {
  private final TargetValidator targetValidator;
  private final ExpiryValidator expiryValidator;
  private final Clock clock;

  public @Inject FindNeighborsPacketDataFactory(
      final TargetValidator targetValidator,
      final ExpiryValidator expiryValidator,
      final Clock clock) {
    this.targetValidator = targetValidator;
    this.expiryValidator = expiryValidator;
    this.clock = clock;
  }

  public FindNeighborsPacketData create(final Bytes target, final long expiration) {
    targetValidator.validate(target);
    expiryValidator.validate(expiration);
    return new FindNeighborsPacketData(target, expiration);
  }

  public FindNeighborsPacketData create(final Bytes target) {
    targetValidator.validate(target);
    return new FindNeighborsPacketData(target, getDefaultExpirationTime());
  }

  private long getDefaultExpirationTime() {
    return clock.instant().getEpochSecond() + PacketData.DEFAULT_EXPIRATION_PERIOD_SEC;
  }
}
