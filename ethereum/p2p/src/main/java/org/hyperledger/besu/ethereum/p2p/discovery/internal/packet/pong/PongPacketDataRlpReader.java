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
import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.PacketDataDeserializer;
import org.hyperledger.besu.ethereum.rlp.MalformedRLPInputException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PongPacketDataRlpReader implements PacketDataDeserializer<PongPacketData> {
  private static final Logger LOG = LoggerFactory.getLogger(PongPacketData.class);

  private final PongPacketDataFactory pongPacketDataFactory;

  public @Inject PongPacketDataRlpReader(final PongPacketDataFactory pongPacketDataFactory) {
    this.pongPacketDataFactory = pongPacketDataFactory;
  }

  @Override
  public PongPacketData readFrom(final RLPInput in) {
    in.enterList();
    final Endpoint to = Endpoint.decodeStandalone(in);
    final Bytes hash = in.readBytes();
    final long expiration = in.readLongScalar();
    UInt64 enrSeq = null;
    if (!in.isEndOfCurrentList()) {
      try {
        enrSeq = UInt64.valueOf(in.readBigIntegerScalar());
        LOG.trace("read PONG enr from scalar");
      } catch (final MalformedRLPInputException malformed) {
        LOG.trace("failed to read PONG enr from scalar, trying as byte array");
        enrSeq = UInt64.fromBytes(in.readBytes());
      }
    }
    in.leaveListLenient();
    return pongPacketDataFactory.create(to, hash, expiration, enrSeq);
  }
}
