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

import org.hyperledger.besu.datatypes.GWei;

public class WithdrawalParameterTestFixture {

  public static final WithdrawalParameter WITHDRAWAL_PARAM_1 =
      createWithdrawal("0x0", "0xffff", "0x0000000000000000000000000000000000000000", "0x0");
  static final WithdrawalParameter WITHDRAWAL_PARAM_2 =
      createWithdrawal(
          "0x1", "0x10000", "0x0100000000000000000000000000000000000000", GWei.ONE.toHexString());

  private static WithdrawalParameter createWithdrawal(
      final String index, final String validatorIndex, final String address, final String amount) {
    return new WithdrawalParameter(index, validatorIndex, address, amount);
  }
}
