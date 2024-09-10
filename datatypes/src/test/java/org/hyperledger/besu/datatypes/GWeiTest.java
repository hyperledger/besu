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
package org.hyperledger.besu.datatypes;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;

import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Test;

class GWeiTest {

  @Test
  void getAsWei() {
    final GWei gwei = GWei.of(1);
    final Wei wei = gwei.getAsWei();
    assertThat(wei.getAsBigInteger()).isEqualTo(1_000_000_000L);
  }

  @Test
  void gWeiFromLongValue() {
    final GWei gwei = GWei.of(1);
    assertThat(gwei.getValue()).isEqualTo(BigInteger.ONE);
  }

  @Test
  void gWeiFromBigIntegerValue() {
    final GWei gwei = GWei.of(BigInteger.TWO);
    assertThat(gwei.getValue()).isEqualTo(BigInteger.TWO);
  }

  @Test
  void gWeiFromUInt64Value() {
    final GWei gwei = GWei.of(UInt64.valueOf(2));
    assertThat(gwei.getValue()).isEqualTo(BigInteger.TWO);
  }

  @Test
  void gWeiFromHexStringValue() {
    final GWei gwei = GWei.fromHexString("0x0000000000000002");
    assertThat(gwei.getValue()).isEqualTo(BigInteger.TWO);
  }

  @Test
  void gWeiToHexString() {
    final GWei gwei = GWei.of(UInt64.valueOf(2));
    assertThat(gwei.toHexString()).isEqualTo("0x0000000000000002");

    final GWei gwei0 = GWei.ZERO;
    assertThat(gwei0.toHexString()).isEqualTo("0x0000000000000000");
    assertThat(gwei0.toShortHexString()).isEqualTo("0x0");
  }
}
