/*
 * Copyright Hyperledger Besu Contributors.
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

public class DepositParameterTestFixture {

  public static final DepositParameter DEPOSIT_PARAM_1 =
      createDeposit(
          "0xb10a4a15bf67b328c9b101d09e5c6ee6672978fdad9ef0d9e2ceffaee99223555d8601f0cb3bcc4ce1af9864779a416e",
          "0x0017a7fcf06faf493d30bbe2632ea7c2383cd86825e12797165de7aa35589483",
          "0x40597307000000",
          "0xa889db8300194050a2636c92a95bc7160515867614b7971a9500cdb62f9c0890217d2901c3241f86fac029428fc106930606154bd9e406d7588934a5f15b837180b17194d6e44bd6de23e43b163dfe12e369dcc75a3852cd997963f158217eb5",
          "0x3f3d080000000000"
      );
  static final DepositParameter DEPOSIT_PARAM_2 =
      createDeposit(
        "0x8706d19a62f28a6a6549f96c5adaebac9124a61d44868ec94f6d2d707c6a2f82c9162071231dfeb40e24bfde4ffdf243",
          "0x006a8dc800c6d8dd6977ef53264e2d030350f0145a91bcd167b4f1c3ea21b271",
          "0x40597307000000",
          "0x801b08ca107b623eca32ee9f9111b4e50eb9cfe19e38204b72de7dc04c5a5e00f61bab96f10842576f66020ce851083f1583dd9a6b73301bea6c245cf51f27cf96aeb018852c5f70bf485d16b957cfe49ca008913346b431e7653ae3ddb23b07",
          "0x0f8b080000000000"
      );

  private static DepositParameter createDeposit(final String pubKey, final String withdrawalCredentials, final String amount, final String signature, final String index) {
    return new DepositParameter(pubKey, withdrawalCredentials, amount, signature, index);
  }
}
