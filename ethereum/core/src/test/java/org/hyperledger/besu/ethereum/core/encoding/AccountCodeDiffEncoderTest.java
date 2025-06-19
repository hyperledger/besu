/*
 * Copyright contributors to Besu.
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
import org.hyperledger.besu.ethereum.core.BlockAccessList.AccountCodeDiff;
import org.hyperledger.besu.ethereum.core.BlockAccessList.CodeChange;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class AccountCodeDiffEncoderTest {

  @Test
  void shouldEncodeAndDecodeAccountCodeDiff() {
    final Address address = Address.fromHexString("0x00000000219ab540356cbb839cbe05303d7705fa");
    final int txIndex = 3;
    final Bytes newCode = Bytes.fromHexString("0x6001600101");

    final AccountCodeDiff original = new AccountCodeDiff(address, new CodeChange(txIndex, newCode));

    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    AccountCodeDiffEncoder.encode(original, output);
    final Bytes encoded = output.encoded();

    final BytesValueRLPInput input = new BytesValueRLPInput(encoded, false);
    final AccountCodeDiff decoded = AccountCodeDiffDecoder.decode(input);

    assertThat(decoded.getAddress()).isEqualTo(address);
    assertThat(decoded.getChange().getTxIndex()).isEqualTo(txIndex);
    assertThat(decoded.getChange().getNewCode()).isEqualTo(newCode);
  }
}
