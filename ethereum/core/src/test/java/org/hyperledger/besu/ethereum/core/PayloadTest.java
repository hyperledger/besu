/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class PayloadTest {

  @Test
  void shouldReturnPayloadBytes() {
    final Bytes input = Bytes.random(20);

    Payload payload = new Payload(input);
    assertThat(payload.getPayloadBytes()).isSameAs(input);
  }

  @Test
  void shouldReturnZeroWhenPayloadIsNull() {
    Payload payload = new Payload(null);
    assertThat(payload.getZeroBytesCount()).isZero();
  }

  @Test
  void shouldCountGetZeroBytesCountCorrectly() {
    final Bytes input = Bytes.fromHexString("0x0001000200");

    Payload payload = new Payload(input);
    long zeroCount = payload.getZeroBytesCount();

    assertThat(zeroCount).isEqualTo(3);
  }

  @Test
  void shouldReturnTrueForSamePayloadBytes() {
    final Bytes input = Bytes.random(20);

    Payload p1 = new Payload(input);
    Payload p2 = new Payload(input);

    assertThat(p1).isEqualTo(p2);
  }

  @Test
  void equals_shouldReturnFalseForDifferentPayloadBytes() {
    final Bytes input1 = Bytes.fromHexString("0x01");
    final Bytes input2 = Bytes.fromHexString("0x02");

    Payload p1 = new Payload(input1);
    Payload p2 = new Payload(input2);

    assertThat(p1).isNotEqualTo(p2);
  }

  @Test
  void shouldBeConsistentWithEquals() {
    final Bytes input = Bytes.random(20);

    Payload p1 = new Payload(input);
    Payload p2 = new Payload(input);

    assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
  }

  @Test
  void shouldContainPayloadBytes() {
    final Bytes input = Bytes.random(20);

    Payload payload = new Payload(input);
    String str = payload.toString();

    assertThat(str).contains("Payload{").contains("payloadBytes=" + input.toHexString());
  }
}
