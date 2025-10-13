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

import org.hyperledger.besu.ethereum.p2p.discovery.internal.packet.PacketDataDeserializer;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.tuweni.bytes.Bytes;

@Singleton
public class FindNeighborsPacketDataRlpReader
    implements PacketDataDeserializer<FindNeighborsPacketData> {
  private final FindNeighborsPacketDataFactory findNeighborsPacketDataFactory;

  public @Inject FindNeighborsPacketDataRlpReader(
      final FindNeighborsPacketDataFactory findNeighborsPacketDataFactory) {
    this.findNeighborsPacketDataFactory = findNeighborsPacketDataFactory;
  }

  @Override
  public FindNeighborsPacketData readFrom(final RLPInput in) {
    in.enterList();
    final Bytes target = in.readBytes();
    final long expiration = in.readLongScalar();
    in.leaveListLenient();
    return findNeighborsPacketDataFactory.create(target, expiration);
  }
}
