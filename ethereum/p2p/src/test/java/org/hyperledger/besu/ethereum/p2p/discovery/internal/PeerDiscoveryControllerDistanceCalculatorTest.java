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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerDistanceCalculator.distance;

import java.util.Random;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class PeerDiscoveryControllerDistanceCalculatorTest {

  @Test
  public void distanceZero() {
    final byte[] id = new byte[64];
    new Random().nextBytes(id);
    assertThat(distance(Bytes.wrap(id), Bytes.wrap(id))).isEqualTo(0);
  }

  @Test
  public void distance1() {
    final Bytes id1 = Bytes.fromHexString("0x8f19400000");
    final Bytes id2 = Bytes.fromHexString("0x8f19400001");
    assertThat(distance(id1, id2)).isEqualTo(1);
  }

  @Test
  public void distance2() {
    final Bytes id1 = Bytes.fromHexString("0x8f19400000");
    final Bytes id2 = Bytes.fromHexString("0x8f19400002");
    assertThat(distance(id1, id2)).isEqualTo(2);
  }

  @Test
  public void distance3() {
    final Bytes id1 = Bytes.fromHexString("0x8f19400000");
    final Bytes id2 = Bytes.fromHexString("0x8f19400004");
    assertThat(distance(id1, id2)).isEqualTo(3);
  }

  @Test
  public void distance9() {
    final Bytes id1 = Bytes.fromHexString("0x8f19400100");
    final Bytes id2 = Bytes.fromHexString("0x8f19400000");
    assertThat(distance(id1, id2)).isEqualTo(9);
  }

  @Test
  public void distance40() {
    final Bytes id1 = Bytes.fromHexString("0x8f19400000");
    final Bytes id2 = Bytes.fromHexString("0x0f19400000");
    assertThat(distance(id1, id2)).isEqualTo(40);
  }

  @Test
  public void distance40_differentLengths() {
    final Bytes id1 = Bytes.fromHexString("0x8f19400000");
    final Bytes id2 = Bytes.fromHexString("0x0f1940000099");
    assertThatThrownBy(() -> distance(id1, id2)).isInstanceOf(AssertionError.class);
  }

  @Test
  public void distanceZero_emptyArrays() {
    final Bytes id1 = Bytes.EMPTY;
    final Bytes id2 = Bytes.EMPTY;
    assertThat(distance(id1, id2)).isEqualTo(0);
  }
}
