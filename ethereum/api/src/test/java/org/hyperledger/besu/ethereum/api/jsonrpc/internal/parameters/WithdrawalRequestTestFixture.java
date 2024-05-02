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

public class WithdrawalRequestTestFixture {

  public static final WithdrawalRequestParameter WITHDRAWAL_REQUEST_PARAMETER_1 =
      new WithdrawalRequestParameter(
          "0x814fae9f487206471b6b0d713cd51a2d35980000",
          "0xb10a4a15bf67b328c9b101d09e5c6ee6672978fdad9ef0d9e2ceffaee99223555d8601f0cb3bcc4ce1af9864779a416e",
          GWei.ONE.toShortHexString());
  static final WithdrawalRequestParameter WITHDRAWAL_REQUEST_PARAMETER_2 =
      new WithdrawalRequestParameter(
          "0x758b8178a9a4b7206d1f648c4a77c515cbac7000",
          "0x8706d19a62f28a6a6549f96c5adaebac9124a61d44868ec94f6d2d707c6a2f82c9162071231dfeb40e24bfde4ffdf243",
          GWei.ONE.toShortHexString());
}
