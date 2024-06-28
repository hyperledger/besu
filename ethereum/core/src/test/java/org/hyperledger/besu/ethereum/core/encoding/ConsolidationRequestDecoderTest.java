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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.ethereum.core.ConsolidationRequest;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ConsolidationRequestDecoderTest {

  @Test
  public void shouldDecodeWithdrawalRequest() {
    final ConsolidationRequest expectedConsolidationRequest =
        new ConsolidationRequest(
            Address.fromHexString("0x814FaE9f487206471B6B0D713cD51a2D35980000"),
            BLSPublicKey.fromHexString(
                "0xb10a4a15bf67b328c9b101d09e5c6ee6672978fdad9ef0d9e2ceffaee99223555d8601f0cb3bcc4ce1af9864779a416e"),
            BLSPublicKey.fromHexString(
                "0xa09a4a15bf67b328c9b101d09e5c6ee6672978f7ad9ef0d9e2c457aee99223555d8601f0cb3bcc4ce1af9864779a416e"));

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    expectedConsolidationRequest.writeTo(out);

    final Request decodedWithdrawalRequest = RequestDecoder.decode(RLP.input(out.encoded()));

    Assertions.assertThat(decodedWithdrawalRequest).isEqualTo(expectedConsolidationRequest);
  }
}
