/*
 * Copyright contributors to Hyperledger Besu
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
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.ethereum.core.ValidatorExit;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class ValidatorExitEncoderTest {
  @Test
  void shouldEncodeExit() {
    final ValidatorExit exit =
        new ValidatorExit(
            Address.fromHexString("0x763c396673F9c391DCe3361A9A71C8E161388000"),
            BLSPublicKey.fromHexString(
                "0xb10a4a15bf67b328c9b101d09e5c6ee6672978fdad9ef0d9e2ceffaee99223555d8601f0cb3bcc4ce1af9864779a416e"));

    final Bytes encoded = ValidatorExitEncoder.encodeOpaqueBytes(exit);

    assertThat(encoded)
        .isEqualTo(
            Bytes.fromHexString(
                "0xf84694763c396673f9c391dce3361a9a71c8e161388000b0b10a4a15bf67b328c9b101d09e5c6ee6672978fdad9ef0d9e2ceffaee99223555d8601f0cb3bcc4ce1af9864779a416e"));
  }
}
