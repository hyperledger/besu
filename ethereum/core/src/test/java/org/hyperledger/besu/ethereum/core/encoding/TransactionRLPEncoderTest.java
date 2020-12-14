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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.AccessList;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.junit.Test;

import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
public class TransactionRLPEncoderTest {
  private static final String FRONTIER_TX_RLP =
      "0xf901fc8032830138808080b901ae60056013565b6101918061001d6000396000f35b3360008190555056006001600060e060020a6000350480630a874df61461003a57806341c0e1b514610058578063a02b161e14610066578063dbbdf0831461007757005b610045600435610149565b80600160a060020a031660005260206000f35b610060610161565b60006000f35b6100716004356100d4565b60006000f35b61008560043560243561008b565b60006000f35b600054600160a060020a031632600160a060020a031614156100ac576100b1565b6100d0565b8060018360005260205260406000208190555081600060005260206000a15b5050565b600054600160a060020a031633600160a060020a031614158015610118575033600160a060020a0316600182600052602052604060002054600160a060020a031614155b61012157610126565b610146565b600060018260005260205260406000208190555080600060005260206000a15b50565b60006001826000526020526040600020549050919050565b600054600160a060020a031633600160a060020a0316146101815761018f565b600054600160a060020a0316ff5b561ca0c5689ed1ad124753d54576dfb4b571465a41900a1dff4058d8adf16f752013d0a01221cbd70ec28c94a3b55ec771bcbc70778d6ee0b51ca7ea9514594c861b1884";
  private static final String EIP1559_TX_RLP =
      "0xf902028032830138808080b901ae60056013565b6101918061001d6000396000f35b3360008190555056006001600060e060020a6000350480630a874df61461003a57806341c0e1b514610058578063a02b161e14610066578063dbbdf0831461007757005b610045600435610149565b80600160a060020a031660005260206000f35b610060610161565b60006000f35b6100716004356100d4565b60006000f35b61008560043560243561008b565b60006000f35b600054600160a060020a031632600160a060020a031614156100ac576100b1565b6100d0565b8060018360005260205260406000208190555081600060005260206000a15b5050565b600054600160a060020a031633600160a060020a031614158015610118575033600160a060020a0316600182600052602052604060002054600160a060020a031614155b61012157610126565b610146565b600060018260005260205260406000208190555080600060005260206000a15b50565b60006001826000526020526040600020549050919050565b600054600160a060020a031633600160a060020a0316146101815761018f565b600054600160a060020a0316ff5b5682020f8201711ca0c5689ed1ad124753d54576dfb4b571465a41900a1dff4058d8adf16f752013d0a01221cbd70ec28c94a3b55ec771bcbc70778d6ee0b51ca7ea9514594c861b1884";

  @Test
  public void encodeFrontierTxNominalCase() {
    final Transaction transaction =
        TransactionRLPDecoder.decode(RLP.input(Bytes.fromHexString(FRONTIER_TX_RLP)));
    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    TransactionRLPEncoder.encode(transaction, output);
    assertThat(FRONTIER_TX_RLP).isEqualTo(output.encoded().toHexString());
  }

  @Test
  public void encodeEIP1559TxNominalCase() {
    ExperimentalEIPs.eip1559Enabled = true;
    final Transaction transaction =
        TransactionRLPDecoder.decode(RLP.input(Bytes.fromHexString(EIP1559_TX_RLP)));
    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    TransactionRLPEncoder.encode(transaction, output);
    assertThat(
            "0xf902028080830138808080b901ae60056013565b6101918061001d6000396000f35b3360008190555056006001600060e060020a6000350480630a874df61461003a57806341c0e1b514610058578063a02b161e14610066578063dbbdf0831461007757005b610045600435610149565b80600160a060020a031660005260206000f35b610060610161565b60006000f35b6100716004356100d4565b60006000f35b61008560043560243561008b565b60006000f35b600054600160a060020a031632600160a060020a031614156100ac576100b1565b6100d0565b8060018360005260205260406000208190555081600060005260206000a15b5050565b600054600160a060020a031633600160a060020a031614158015610118575033600160a060020a0316600182600052602052604060002054600160a060020a031614155b61012157610126565b610146565b600060018260005260205260406000208190555080600060005260206000a15b50565b60006001826000526020526040600020549050919050565b600054600160a060020a031633600160a060020a0316146101815761018f565b600054600160a060020a0316ff5b5682020f8201711ca0c5689ed1ad124753d54576dfb4b571465a41900a1dff4058d8adf16f752013d0a01221cbd70ec28c94a3b55ec771bcbc70778d6ee0b51ca7ea9514594c861b1884")
        .isEqualTo(output.encoded().toHexString());
    ExperimentalEIPs.eip1559Enabled = ExperimentalEIPs.EIP1559_ENABLED_DEFAULT_VALUE;
  }

  @Test
  public void encodeEIP1559TxFailureNotEnabled() {
    ExperimentalEIPs.eip1559Enabled = false;
    assertThatThrownBy(
            () ->
                TransactionRLPEncoder.encode(
                    TransactionRLPDecoder.decode(RLP.input(Bytes.fromHexString(EIP1559_TX_RLP))),
                    new BytesValueRLPOutput()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("EIP-1559 feature flag");
    ExperimentalEIPs.eip1559Enabled = ExperimentalEIPs.EIP1559_ENABLED_DEFAULT_VALUE;
  }

  @Test
  public void blockBodyWithLegacyAndEIP2930TransactionsRoundTrips() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final List<Address> accessedAddresses = List.of(gen.address(), gen.address(), gen.address());
    final List<Map.Entry<Address, List<Bytes32>>> accessedStorage = new ArrayList<>();
    for (int i = 0; i < accessedAddresses.size(); ++i) {
      accessedStorage.add(
          new AbstractMap.SimpleEntry<>(
              accessedAddresses.get(i),
              Stream.generate(gen::bytes32).limit(2 * i).collect(toUnmodifiableList())));
    }
    final BlockBody blockBody =
        new BlockBody(
            List.of(
                Transaction.builder()
                    .type(TransactionType.FRONTIER)
                    .nonce(42)
                    .gasLimit(654321)
                    .gasPrice(Wei.of(2))
                    .value(Wei.of(1337))
                    .payload(Bytes.EMPTY)
                    .signAndBuild(SECP256K1.KeyPair.generate()),
                Transaction.builder()
                    .type(TransactionType.ACCESS_LIST)
                    .chainId(BigInteger.ONE)
                    .nonce(1)
                    .gasPrice(Wei.of(500))
                    .gasLimit(100000)
                    .to(gen.address())
                    .value(Wei.of(1))
                    .payload(Bytes.EMPTY)
                    .accessList(new AccessList(accessedStorage))
                    // TODO figure out yparity bit bit
                    .signAndBuild(SECP256K1.KeyPair.generate())),
            emptyList());

    assertThat(
            BlockBody.readFrom(
                RLP.input(RLP.encode(blockBody::writeTo)), new MainnetBlockHeaderFunctions()))
        .isEqualTo(blockBody);
  }
}
