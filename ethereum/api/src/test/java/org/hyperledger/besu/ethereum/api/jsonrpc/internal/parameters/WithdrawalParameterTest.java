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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.WithdrawalParameterTestFixture.WITHDRAWAL_PARAM_1;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.ethereum.core.Withdrawal;

import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Test;

public class WithdrawalParameterTest {

  @Test
  public void toWithdrawal() {
    Withdrawal expected =
        new Withdrawal(
            UInt64.fromHexString("0x0"),
            UInt64.fromHexString("0xffff"),
            Address.fromHexString("0x0000000000000000000000000000000000000000"),
            GWei.of(0L));
    assertThat(WITHDRAWAL_PARAM_1.toWithdrawal()).isEqualTo(expected);
  }

  @Test
  public void fromWithdrawal() {
    Withdrawal withdrawal =
        new Withdrawal(
            UInt64.fromHexString("0x0"),
            UInt64.fromHexString("0xffff"),
            Address.fromHexString("0x0000000000000000000000000000000000000000"),
            GWei.of(0L));
    assertThat(WithdrawalParameter.fromWithdrawal(withdrawal)).isEqualTo(WITHDRAWAL_PARAM_1);
  }
}
