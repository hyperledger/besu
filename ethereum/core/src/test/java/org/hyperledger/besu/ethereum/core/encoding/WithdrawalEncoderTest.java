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
package org.hyperledger.besu.ethereum.core.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.ethereum.core.Withdrawal;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Test;

class WithdrawalEncoderTest {
  public static final String WITHDRAWAL_ZERO_CASE =
      "0xd8808094000000000000000000000000000000000000000080";
  public static final String WITHDRAWAL_MAX_VALUE =
      "0xf088ffffffffffffffff88ffffffffffffffff94ffffffffffffffffffffffffffffffffffffffff88ffffffffffffffff";
  public static final Address MAX_ADDRESS = Address.fromHexString("ff".repeat(20));

  @Test
  void shouldEncodeWithdrawalForZeroCase() {
    final Withdrawal withdrawal = new Withdrawal(UInt64.ZERO, UInt64.ZERO, Address.ZERO, GWei.ZERO);
    final Bytes bytes = WithdrawalEncoder.encodeOpaqueBytes(withdrawal);
    assertThat(bytes.toHexString()).isEqualTo(WITHDRAWAL_ZERO_CASE);
  }

  @Test
  void shouldEncodeWithdrawalForMaxValues() {
    final Withdrawal withdrawal =
        new Withdrawal(UInt64.MAX_VALUE, UInt64.MAX_VALUE, MAX_ADDRESS, GWei.MAX_GWEI);
    final Bytes bytes = WithdrawalEncoder.encodeOpaqueBytes(withdrawal);
    assertThat(bytes.toHexString()).isEqualTo(WITHDRAWAL_MAX_VALUE);
  }

  @Test
  void shouldEncode() {
    final UInt64 index = UInt64.valueOf(3);
    final UInt64 validatorIndex = UInt64.valueOf(1);
    final Address address = Address.fromHexString("0xdeadbeef");
    final GWei amount = GWei.of(5);
    final Withdrawal withdrawal = new Withdrawal(index, validatorIndex, address, amount);
    final Bytes encoded = WithdrawalEncoder.encodeOpaqueBytes(withdrawal);

    assertThat(encoded)
        .isEqualTo(Bytes.fromHexString("0xd803019400000000000000000000000000000000deadbeef05"));
  }
}
