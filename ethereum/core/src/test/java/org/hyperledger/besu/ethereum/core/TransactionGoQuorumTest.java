/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class TransactionGoQuorumTest {

  private static final RLPInput ETHEREUM_PUBLIC_TX_RLP =
      toRLP(
          "0xf901fc8032830138808080b901ae60056013565b6101918061001d6000396000f35b3360008190555056006001600060e060020a6000350480630a874df61461003a57806341c0e1b514610058578063a02b161e14610066578063dbbdf0831461007757005b610045600435610149565b80600160a060020a031660005260206000f35b610060610161565b60006000f35b6100716004356100d4565b60006000f35b61008560043560243561008b565b60006000f35b600054600160a060020a031632600160a060020a031614156100ac576100b1565b6100d0565b8060018360005260205260406000208190555081600060005260206000a15b5050565b600054600160a060020a031633600160a060020a031614158015610118575033600160a060020a0316600182600052602052604060002054600160a060020a031614155b61012157610126565b610146565b600060018260005260205260406000208190555080600060005260206000a15b50565b60006001826000526020526040600020549050919050565b600054600160a060020a031633600160a060020a0316146101815761018f565b600054600160a060020a0316ff5b561ca0c5689ed1ad124753d54576dfb4b571465a41900a1dff4058d8adf16f752013d0a01221cbd70ec28c94a3b55ec771bcbc70778d6ee0b51ca7ea9514594c861b1884");
  private static final RLPInput GOQUORUM_PRIVATE_TX_RLP_V37 =
      toRLP(
          "0xf88d0b808347b7608080b840290a80a37d198ff06abe189b638ff53ac8a8dc51a0aff07609d2aa75342783ae493b3e3c6b564c0eebe49284b05a0726fb33087b9e0231d349ea0c7b5661c8c525a07144db7045a395e608cda6ab051c86cc4fb42e319960b82087f3b26f0cbc3c2da00223ac129b22aec7a6c2ace3c3ef39c5eaaa54070fd82d8ee2140b0e70b1dca9");
  private static final RLPInput GOQUORUM_PRIVATE_TX_RLP_V38 =
      toRLP(
          "0xf88d0b808347b7608080b840290a80a37d198ff06abe189b638ff53ac8a8dc51a0aff07609d2aa75342783ae493b3e3c6b564c0eebe49284b05a0726fb33087b9e0231d349ea0c7b5661c8c526a07144db7045a395e608cda6ab051c86cc4fb42e319960b82087f3b26f0cbc3c2da00223ac129b22aec7a6c2ace3c3ef39c5eaaa54070fd82d8ee2140b0e70b1dca9");

  private static final boolean goQuorumCompatibilityMode = true;

  @Test
  public void givenPublicTransaction_assertThatIsGoQuorumFlagIsFalse() {
    final Transaction transaction =
        TransactionDecoder.decodeForWire(ETHEREUM_PUBLIC_TX_RLP, goQuorumCompatibilityMode);

    assertThat(transaction.isGoQuorumPrivateTransaction(goQuorumCompatibilityMode)).isFalse();
    assertThat(transaction.hasCostParams()).isTrue();
  }

  @Test
  public void givenGoQuorumTransactionV37_assertThatIsGoQuorumFlagIsTrue() {
    final Transaction transaction =
        TransactionDecoder.decodeForWire(GOQUORUM_PRIVATE_TX_RLP_V37, goQuorumCompatibilityMode);

    assertThat(transaction.getV()).isEqualTo(37);
    assertThat(transaction.isGoQuorumPrivateTransaction(goQuorumCompatibilityMode)).isTrue();
    assertThat(transaction.hasCostParams()).isFalse();
  }

  @Test
  public void givenGoQuorumTransactionV38_assertThatIsGoQuorumFlagIsTrue() {
    final Transaction transaction =
        TransactionDecoder.decodeForWire(GOQUORUM_PRIVATE_TX_RLP_V38, goQuorumCompatibilityMode);

    assertThat(transaction.getV()).isEqualTo(38);
    assertThat(transaction.isGoQuorumPrivateTransaction(goQuorumCompatibilityMode)).isTrue();
    assertThat(transaction.hasCostParams()).isFalse();
  }

  @Test
  public void givenTransactionWithChainId_assertThatIsGoQuorumFlagIsFalse() {
    final Transaction transaction = Transaction.builder().chainId(BigInteger.valueOf(0)).build();
    assertThat(transaction.isGoQuorumPrivateTransaction(goQuorumCompatibilityMode)).isFalse();
  }

  @Test
  public void givenTransactionWithoutChainIdAndV37_assertThatIsGoQuorumFlagIsTrue() {
    final Transaction transaction = Transaction.builder().v(BigInteger.valueOf(37)).build();
    assertThat(transaction.isGoQuorumPrivateTransaction(goQuorumCompatibilityMode)).isTrue();
  }

  private static RLPInput toRLP(final String bytes) {
    return RLP.input(Bytes.fromHexString(bytes));
  }
}
