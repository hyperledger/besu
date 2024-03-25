/*
 * Copyright contributors to Hyperledger Besu
 *
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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.ValidatorExitTestFixture.VALIDATOR_EXIT_PARAMETER_1;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.ethereum.core.ValidatorExit;

import org.junit.jupiter.api.Test;

public class ValidatorExitParameterTest {

  @Test
  public void toValidatorExit() {
    ValidatorExit expected =
        new ValidatorExit(
            Address.fromHexString("0x814FaE9f487206471B6B0D713cD51a2D35980000"),
            BLSPublicKey.fromHexString(
                "0xb10a4a15bf67b328c9b101d09e5c6ee6672978fdad9ef0d9e2ceffaee99223555d8601f0cb3bcc4ce1af9864779a416e"));
    assertThat(VALIDATOR_EXIT_PARAMETER_1.toValidatorExit()).isEqualTo(expected);
  }

  @Test
  public void fromValidatorExit() {
    ValidatorExit validatorExit =
        new ValidatorExit(
            Address.fromHexString("0x814FaE9f487206471B6B0D713cD51a2D35980000"),
            BLSPublicKey.fromHexString(
                "0xb10a4a15bf67b328c9b101d09e5c6ee6672978fdad9ef0d9e2ceffaee99223555d8601f0cb3bcc4ce1af9864779a416e"));

    assertThat(ValidatorExitParameter.fromValidatorExit(validatorExit))
        .isEqualTo(VALIDATOR_EXIT_PARAMETER_1);
  }
}
