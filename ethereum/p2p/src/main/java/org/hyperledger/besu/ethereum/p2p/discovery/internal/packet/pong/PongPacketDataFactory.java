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
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.PacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.validation.ExpiryValidator;

import java.time.Clock;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;

@Singleton
public class PongPacketDataFactory {

  private final ExpiryValidator expiryValidator;
  private final Clock clock;

  public @Inject PongPacketDataFactory(final ExpiryValidator expiryValidator, final Clock clock) {
    this.expiryValidator = expiryValidator;
    this.clock = clock;
  }

  public PongPacketData create(
      final Endpoint to, final Bytes pingHash, final long expiration, final UInt64 enrSeq) {
    expiryValidator.validate(expiration);
    return new PongPacketData(to, pingHash, expiration, enrSeq);
  }

  public PongPacketData create(final Endpoint to, final Bytes pingHash, final UInt64 enrSeq) {
    return new PongPacketData(to, pingHash, getDefaultExpirationTime(), enrSeq);
  }

  private long getDefaultExpirationTime() {
    return clock.instant().getEpochSecond() + PacketData.DEFAULT_EXPIRATION_PERIOD_SEC;
  }
}
