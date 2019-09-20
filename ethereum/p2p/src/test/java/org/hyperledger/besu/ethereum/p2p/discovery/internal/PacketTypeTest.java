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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class PacketTypeTest {

  @Test
  public void shouldReturnEmptyPacketTypeForNegativeByte() {
    assertThat(PacketType.forByte((byte) -1)).isEmpty();
  }

  @Test
  public void shouldReturnEmptyPacketTypeForByteAboveMaxValue() {
    assertThat(PacketType.forByte(Byte.MAX_VALUE)).isEmpty();
  }

  @Test
  public void shouldWorkForEveryPossibleByteValue() {
    for (int b = Byte.MIN_VALUE; b <= Byte.MAX_VALUE; b++) {
      assertThat(PacketType.forByte((byte) b)).isNotNull();
    }
  }

  @Test
  public void shouldReturnEachPacketTypeByByte() {
    for (final PacketType packetType : PacketType.values()) {
      assertThat(PacketType.forByte(packetType.getValue())).contains(packetType);
    }
  }
}
