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
package org.hyperledger.besu.ethereum.core.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class TransactionBlobEncodingTest {

  private static final String BLOB =
      "0xb8df03f8dc850120b996ed05840bebc200843b9aca0783033450948a185c5a7941b20253a2923690dd54a9e7bfd0a980b844a8d55bda000000000000000000000000573d9cd570267bb9d1547192e51e5c8d017d70340000000000000000000000000000000000000000000000000000000000000000c08411e1a300e1a0011df88a2971c8a7ac494a7ba37ec1acaa1fc1edeeb38c839b5d1693d47b69b080a0f59e881073e74c4b4a7a49d92723a945d314d51636f389caf1c8590991c33f84a05dabba003f4ef8a7ee435e9039672b2933acdced51b499229bfe421af32f0939";

  @Test
  public void test() {
    final Transaction transaction =
        TransactionDecoder.decodeForWire(RLP.input(Bytes.fromHexString(BLOB)));
    final BytesValueRLPOutput output = new BytesValueRLPOutput();

    TransactionEncoder.encodeForWire(transaction, output);
    assertThat(output.encoded().toHexString()).isEqualTo(BLOB);
  }
}
