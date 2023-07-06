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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Arrays;
import java.util.Optional;
import javax.annotation.concurrent.Immutable;

public enum PacketType {
  PING(0x01, PingPacketData::readFrom),
  PONG(0x02, PongPacketData::readFrom),
  FIND_NEIGHBORS(0x03, FindNeighborsPacketData::readFrom),
  NEIGHBORS(0x04, NeighborsPacketData::readFrom),
  ENR_REQUEST(0x05, ENRRequestPacketData::readFrom),
  ENR_RESPONSE(0x06, ENRResponsePacketData::readFrom);

  private static final int MAX_VALUE = 0x7F;
  private static final int BYTE_MASK = 0xFF;

  private static final PacketType[] INDEX = new PacketType[PacketType.MAX_VALUE];

  static {
    Arrays.stream(values()).forEach(type -> INDEX[type.value] = type);
  }

  private final byte value;
  private final Deserializer<?> deserializer;

  public static Optional<PacketType> forByte(final byte b) {
    return b >= MAX_VALUE || b < 0 ? Optional.empty() : Optional.ofNullable(INDEX[b]);
  }

  PacketType(final int value, final Deserializer<?> deserializer) {
    checkArgument(value <= MAX_VALUE, "Packet type ID must be in range [0x00, 0x80)");
    this.deserializer = deserializer;
    this.value = (byte) (value & BYTE_MASK);
  }

  public byte getValue() {
    return value;
  }

  public Deserializer<?> getDeserializer() {
    return deserializer;
  }

  @FunctionalInterface
  @Immutable
  public interface Deserializer<T extends PacketData> {
    T deserialize(RLPInput in);
  }
}
