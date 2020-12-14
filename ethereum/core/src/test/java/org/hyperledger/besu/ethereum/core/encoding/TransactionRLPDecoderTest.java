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

import org.hyperledger.besu.config.GoQuorumOptions;
import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class TransactionRLPDecoderTest {

  private static final String FRONTIER_TX_RLP =
      "0xf901fc8032830138808080b901ae60056013565b6101918061001d6000396000f35b3360008190555056006001600060e060020a6000350480630a874df61461003a57806341c0e1b514610058578063a02b161e14610066578063dbbdf0831461007757005b610045600435610149565b80600160a060020a031660005260206000f35b610060610161565b60006000f35b6100716004356100d4565b60006000f35b61008560043560243561008b565b60006000f35b600054600160a060020a031632600160a060020a031614156100ac576100b1565b6100d0565b8060018360005260205260406000208190555081600060005260206000a15b5050565b600054600160a060020a031633600160a060020a031614158015610118575033600160a060020a0316600182600052602052604060002054600160a060020a031614155b61012157610126565b610146565b600060018260005260205260406000208190555080600060005260206000a15b50565b60006001826000526020526040600020549050919050565b600054600160a060020a031633600160a060020a0316146101815761018f565b600054600160a060020a0316ff5b561ca0c5689ed1ad124753d54576dfb4b571465a41900a1dff4058d8adf16f752013d0a01221cbd70ec28c94a3b55ec771bcbc70778d6ee0b51ca7ea9514594c861b1884";
  private static final String EIP1559_TX_RLP =
      "0xf902028032830138808080b901ae60056013565b6101918061001d6000396000f35b3360008190555056006001600060e060020a6000350480630a874df61461003a57806341c0e1b514610058578063a02b161e14610066578063dbbdf0831461007757005b610045600435610149565b80600160a060020a031660005260206000f35b610060610161565b60006000f35b6100716004356100d4565b60006000f35b61008560043560243561008b565b60006000f35b600054600160a060020a031632600160a060020a031614156100ac576100b1565b6100d0565b8060018360005260205260406000208190555081600060005260206000a15b5050565b600054600160a060020a031633600160a060020a031614158015610118575033600160a060020a0316600182600052602052604060002054600160a060020a031614155b61012157610126565b610146565b600060018260005260205260406000208190555080600060005260206000a15b50565b60006001826000526020526040600020549050919050565b600054600160a060020a031633600160a060020a0316146101815761018f565b600054600160a060020a0316ff5b5682020f8201711ca0c5689ed1ad124753d54576dfb4b571465a41900a1dff4058d8adf16f752013d0a01221cbd70ec28c94a3b55ec771bcbc70778d6ee0b51ca7ea9514594c861b1884";
  private static final String GOQUORUM_PRIVATE_TX_RLP =
      "0xf88d0b808347b7608080b840290a80a37d198ff06abe189b638ff53ac8a8dc51a0aff07609d2aa75342783ae493b3e3c6b564c0eebe49284b05a0726fb33087b9e0231d349ea0c7b5661c8c526a07144db7045a395e608cda6ab051c86cc4fb42e319960b82087f3b26f0cbc3c2da00223ac129b22aec7a6c2ace3c3ef39c5eaaa54070fd82d8ee2140b0e70b1dca9";

  @Test
  public void decodeGoQuorumPrivateTransactionRlp() {
    GoQuorumOptions.goquorumCompatibilityMode = true;
    RLPInput input = RLP.input(Bytes.fromHexString(GOQUORUM_PRIVATE_TX_RLP));

    final Transaction transaction = TransactionRLPDecoder.decode(input);
    assertThat(transaction).isNotNull();
    assertThat(transaction.getV()).isEqualTo(38);
    assertThat(transaction.getSender())
        .isEqualByComparingTo(Address.fromHexString("0xed9d02e382b34818e88b88a309c7fe71e65f419d"));
    GoQuorumOptions.goquorumCompatibilityMode =
        GoQuorumOptions.GOQUORUM_COMPATIBILITY_MODE_DEFAULT_VALUE;
  }

  @Test
  public void decodeFrontierNominalCase() {
    final Transaction transaction =
        TransactionRLPDecoder.decode(RLP.input(Bytes.fromHexString(FRONTIER_TX_RLP)));
    assertThat(transaction).isNotNull();
    assertThat(transaction.getGasPrice()).isEqualByComparingTo(Wei.of(50L));
    assertThat(transaction.getGasPremium()).isEmpty();
    assertThat(transaction.getFeeCap()).isEmpty();
  }

  @Test
  public void decodeEIP1559NominalCase() {
    ExperimentalEIPs.eip1559Enabled = true;
    final Transaction transaction =
        TransactionRLPDecoder.decode(RLP.input(Bytes.fromHexString(EIP1559_TX_RLP)));
    assertThat(transaction).isNotNull();
    assertThat(transaction.getGasPremium()).hasValue(Wei.of(527L));
    assertThat(transaction.getFeeCap()).hasValue(Wei.of(369L));
    ExperimentalEIPs.eip1559Enabled = ExperimentalEIPs.EIP1559_ENABLED_DEFAULT_VALUE;
  }
}
