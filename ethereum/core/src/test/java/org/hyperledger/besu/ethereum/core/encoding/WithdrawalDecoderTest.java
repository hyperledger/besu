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

class WithdrawalDecoderTest {

  @Test
  void shouldDecodeWithdrawalForZeroCase() {
    Withdrawal withdrawal =
        WithdrawalDecoder.decodeOpaqueBytes(
            Bytes.fromHexString(WithdrawalEncoderTest.WITHDRAWAL_ZERO_CASE));
    assertThat(withdrawal.getIndex()).isEqualTo(UInt64.ZERO);
    assertThat(withdrawal.getValidatorIndex()).isEqualTo(UInt64.ZERO);
    assertThat(withdrawal.getAddress()).isEqualTo(Address.ZERO);
    assertThat(withdrawal.getAmount()).isEqualTo(GWei.ZERO);
  }

  @Test
  void shouldDecodeWithdrawalForMaxValue() {
    Withdrawal withdrawal =
        WithdrawalDecoder.decodeOpaqueBytes(
            Bytes.fromHexString(WithdrawalEncoderTest.WITHDRAWAL_MAX_VALUE));
    assertThat(withdrawal.getIndex()).isEqualTo(UInt64.MAX_VALUE);
    assertThat(withdrawal.getValidatorIndex()).isEqualTo(UInt64.MAX_VALUE);
    assertThat(withdrawal.getAddress()).isEqualTo(WithdrawalEncoderTest.MAX_ADDRESS);
    assertThat(withdrawal.getAmount()).isEqualTo(GWei.MAX_GWEI);
  }

  @Test
  void shouldDecodeWithdrawal() {
    final Withdrawal expectedWithdrawal =
        new Withdrawal(
            UInt64.valueOf(3), UInt64.valueOf(1), Address.fromHexString("0xdeadbeef"), GWei.of(5));
    final Withdrawal withdrawal =
        WithdrawalDecoder.decodeOpaqueBytes(
            Bytes.fromHexString("0xd803019400000000000000000000000000000000deadbeef05"));

    assertThat(withdrawal).isEqualTo(expectedWithdrawal);
  }
}
