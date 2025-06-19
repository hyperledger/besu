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
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockAccessList.AccountBalanceDiff;
import org.hyperledger.besu.ethereum.core.BlockAccessList.BalanceChange;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class AccountBalanceDiffEncoderTest {

  @Test
  void shouldEncodeAndDecodeAccountBalanceDiff() {
    final Address address = Address.fromHexString("0x00000000219ab540356cbb839cbe05303d7705fa");
    Wei.fromEth(1).toBigInteger();
    final BalanceChange change1 = new BalanceChange(0, Wei.fromEth(3).toBigInteger());
    final BalanceChange change2 = new BalanceChange(1, Wei.fromEth(5).toBigInteger().negate());

    final AccountBalanceDiff diff = new AccountBalanceDiff(address, List.of(change1, change2));

    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    AccountBalanceDiffEncoder.encode(diff, output);
    final Bytes encoded = output.encoded();

    final BytesValueRLPInput input = new BytesValueRLPInput(encoded, false);
    final AccountBalanceDiff decoded = AccountBalanceDiffDecoder.decode(input);

    assertThat(decoded.getAddress()).isEqualTo(address);
    assertThat(decoded.getBalanceChanges()).hasSize(2);

    final BalanceChange decodedChange1 = decoded.getBalanceChanges().get(0);
    assertThat(decodedChange1.getTxIndex()).isEqualTo(0);
    assertThat(decodedChange1.getDelta()).isEqualTo(change1.getDelta());

    final BalanceChange decodedChange2 = decoded.getBalanceChanges().get(1);
    assertThat(decodedChange2.getTxIndex()).isEqualTo(1);
    assertThat(decodedChange2.getDelta()).isEqualTo(change2.getDelta());
  }

  @Test
  void shouldEncodeAndDecodeAccountBalanceDiffWithEmptyChanges() {
    final Address address = Address.fromHexString("0x00000000219ab540356cbb839cbe05303d7705fa");
    final AccountBalanceDiff diff = new AccountBalanceDiff(address, List.of());

    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    AccountBalanceDiffEncoder.encode(diff, output);
    final Bytes encoded = output.encoded();

    final BytesValueRLPInput input = new BytesValueRLPInput(encoded, false);
    final AccountBalanceDiff decoded = AccountBalanceDiffDecoder.decode(input);

    assertThat(decoded.getAddress()).isEqualTo(address);
    assertThat(decoded.getBalanceChanges()).isEmpty();
  }
}
