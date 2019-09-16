/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerDistanceCalculator.distance;

import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Random;

import org.junit.Test;

public class PeerDiscoveryControllerDistanceCalculatorTest {

  @Test
  public void distanceZero() {
    final byte[] id = new byte[64];
    new Random().nextBytes(id);
    assertThat(distance(BytesValue.wrap(id), BytesValue.wrap(id))).isEqualTo(0);
  }

  @Test
  public void distance1() {
    final BytesValue id1 = BytesValue.fromHexString("0x8f19400000");
    final BytesValue id2 = BytesValue.fromHexString("0x8f19400001");
    assertThat(distance(id1, id2)).isEqualTo(1);
  }

  @Test
  public void distance2() {
    final BytesValue id1 = BytesValue.fromHexString("0x8f19400000");
    final BytesValue id2 = BytesValue.fromHexString("0x8f19400002");
    assertThat(distance(id1, id2)).isEqualTo(2);
  }

  @Test
  public void distance3() {
    final BytesValue id1 = BytesValue.fromHexString("0x8f19400000");
    final BytesValue id2 = BytesValue.fromHexString("0x8f19400004");
    assertThat(distance(id1, id2)).isEqualTo(3);
  }

  @Test
  public void distance9() {
    final BytesValue id1 = BytesValue.fromHexString("0x8f19400100");
    final BytesValue id2 = BytesValue.fromHexString("0x8f19400000");
    assertThat(distance(id1, id2)).isEqualTo(9);
  }

  @Test
  public void distance40() {
    final BytesValue id1 = BytesValue.fromHexString("0x8f19400000");
    final BytesValue id2 = BytesValue.fromHexString("0x0f19400000");
    assertThat(distance(id1, id2)).isEqualTo(40);
  }

  @Test(expected = AssertionError.class)
  public void distance40_differentLengths() {
    final BytesValue id1 = BytesValue.fromHexString("0x8f19400000");
    final BytesValue id2 = BytesValue.fromHexString("0x0f1940000099");
    assertThat(distance(id1, id2)).isEqualTo(40);
  }

  @Test
  public void distanceZero_emptyArrays() {
    final BytesValue id1 = BytesValue.EMPTY;
    final BytesValue id2 = BytesValue.EMPTY;
    assertThat(distance(id1, id2)).isEqualTo(0);
  }
}
