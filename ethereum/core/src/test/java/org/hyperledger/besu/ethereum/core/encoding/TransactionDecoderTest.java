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
package org.hyperledger.besu.ethereum.core.encoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.evm.account.Account.MAX_NONCE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class TransactionDecoderTest {

  private static final String FRONTIER_TX_RLP =
      "0xf901fc8032830138808080b901ae60056013565b6101918061001d6000396000f35b3360008190555056006001600060e060020a6000350480630a874df61461003a57806341c0e1b514610058578063a02b161e14610066578063dbbdf0831461007757005b610045600435610149565b80600160a060020a031660005260206000f35b610060610161565b60006000f35b6100716004356100d4565b60006000f35b61008560043560243561008b565b60006000f35b600054600160a060020a031632600160a060020a031614156100ac576100b1565b6100d0565b8060018360005260205260406000208190555081600060005260206000a15b5050565b600054600160a060020a031633600160a060020a031614158015610118575033600160a060020a0316600182600052602052604060002054600160a060020a031614155b61012157610126565b610146565b600060018260005260205260406000208190555080600060005260206000a15b50565b60006001826000526020526040600020549050919050565b600054600160a060020a031633600160a060020a0316146101815761018f565b600054600160a060020a0316ff5b561ca0c5689ed1ad124753d54576dfb4b571465a41900a1dff4058d8adf16f752013d0a01221cbd70ec28c94a3b55ec771bcbc70778d6ee0b51ca7ea9514594c861b1884";
  private static final String EIP1559_TX_RLP =
      "b8a902f8a686796f6c6f7632800285012a05f20082753094000000000000000000000000000000000000aaaa8080f838f794000000000000000000000000000000000000aaaae1a0000000000000000000000000000000000000000000000000000000000000000001a00c1d69648e348fe26155b45de45004f0e4195f6352d8f0935bc93e98a3e2a862a060064e5b9765c0ac74223b0cf49635c59ae0faf82044fd17bcc68a549ade6f95";
  private static final String GOQUORUM_PRIVATE_TX_RLP =
      "0xf88d0b808347b7608080b840290a80a37d198ff06abe189b638ff53ac8a8dc51a0aff07609d2aa75342783ae493b3e3c6b564c0eebe49284b05a0726fb33087b9e0231d349ea0c7b5661c8c526a07144db7045a395e608cda6ab051c86cc4fb42e319960b82087f3b26f0cbc3c2da00223ac129b22aec7a6c2ace3c3ef39c5eaaa54070fd82d8ee2140b0e70b1dca9";
  private static final String NONCE_64_BIT_MAX_MINUS_2_TX_RLP =
      "0xf86788fffffffffffffffe0182520894095e7baea6a6c7c4c2dfeb977efac326af552d8780801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff759b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56c0b28ad43601b4ab949f53faa07bd2c804";

  @Test
  void decodeGoQuorumPrivateTransactionRlp() {
    boolean goQuorumCompatibilityMode = true;
    RLPInput input = RLP.input(Bytes.fromHexString(GOQUORUM_PRIVATE_TX_RLP));

    final Transaction transaction =
        TransactionDecoder.decodeForWire(input, goQuorumCompatibilityMode);
    assertThat(transaction).isNotNull();
    assertThat(transaction.getV()).isEqualTo(38);
    assertThat(transaction.getSender())
        .isEqualByComparingTo(Address.fromHexString("0xed9d02e382b34818e88b88a309c7fe71e65f419d"));
  }

  @Test
  void decodeFrontierNominalCase() {
    final Transaction transaction =
        TransactionDecoder.decodeForWire(RLP.input(Bytes.fromHexString(FRONTIER_TX_RLP)));
    assertThat(transaction).isNotNull();
    assertThat(transaction.getGasPrice().get()).isEqualByComparingTo(Wei.of(50L));
    assertThat(transaction.getMaxPriorityFeePerGas()).isEmpty();
    assertThat(transaction.getMaxFeePerGas()).isEmpty();
  }

  @Test
  void decodeEIP1559NominalCase() {
    final Transaction transaction =
        TransactionDecoder.decodeForWire(RLP.input(Bytes.fromHexString(EIP1559_TX_RLP)));
    assertThat(transaction).isNotNull();
    assertThat(transaction.getMaxPriorityFeePerGas()).hasValue(Wei.of(2L));
    assertThat(transaction.getMaxFeePerGas()).hasValue(Wei.of(new BigInteger("5000000000", 10)));
  }

  @Test
  void doesNotDecodeEIP1559WithLargeMaxFeePerGasOrLargeMaxPriorityFeePerGas() {
    final String txWithBigFees =
        "0x02f84e0101a1648a5f8b2dcad5ea5ba6b720ff069c1d87c21a4a6a5b3766b39e2c2792367bb066a1ffa5ffaf5b0560d3a9fb186c2ede2ae6751bc0b4fef9107cf36389630b6196a38805800180c0010203";
    final boolean goQuorumCompatibilityMode = false;
    assertThatThrownBy(
            () ->
                TransactionDecoder.decodeOpaqueBytes(
                    Bytes.fromHexString(txWithBigFees), goQuorumCompatibilityMode))
        .isInstanceOf(RLPException.class);
  }

  @Test
  void shouldDecodeWithHighNonce() {
    final Transaction transaction =
        TransactionDecoder.decodeForWire(
            RLP.input(Bytes.fromHexString(NONCE_64_BIT_MAX_MINUS_2_TX_RLP)));
    assertThat(transaction).isNotNull();
    assertThat(transaction.getNonce()).isEqualTo(MAX_NONCE - 1);
  }
}
