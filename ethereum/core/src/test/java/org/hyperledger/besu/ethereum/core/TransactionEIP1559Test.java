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

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.math.BigInteger;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.After;
import org.junit.Test;

public class TransactionEIP1559Test {
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  private static final String PRIVATE_KEY =
      "0xc87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3";

  @After
  public void reset() {
    ExperimentalEIPs.eip1559Enabled = ExperimentalEIPs.EIP1559_ENABLED_DEFAULT_VALUE;
  }

  @Test
  public void buildEip1559Transaction() {
    /*final List<AccessListEntry> accessListEntries =
    List.of(
        new AccessListEntry(
            Address.fromHexString("0x000000000000000000000000000000000000aaaa"),
            List.of(Bytes32.ZERO)));*/
    final Transaction tx =
        Transaction.builder()
            .chainId(new BigInteger("7822", 10))
            .nonce(1)
            .value(Wei.fromEth(2))
            .gasLimit(30000)
            .gasPremium(Wei.of(1000000000))
            .payload(Bytes.EMPTY.trimLeadingZeros())
            .feeCap(Wei.of(new BigInteger("5000000000", 10)))
            // .gasPrice(Wei.of(1000000000))
            .to(Address.fromHexString("0xC3298C6341f82468309302611e24D3003Bc79B46"))
            .guessType()
            .signAndBuild(keyPair(PRIVATE_KEY));
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    tx.writeTo(out);
    System.out.println(out.encoded().toHexString());
    // final String raw =
    // "b8a902f8a686796f6c6f7632800285012a05f20082753094000000000000000000000000000000000000aaaa8080f838f794000000000000000000000000000000000000aaaae1a0000000000000000000000000000000000000000000000000000000000000000001a00c1d69648e348fe26155b45de45004f0e4195f6352d8f0935bc93e98a3e2a862a060064e5b9765c0ac74223b0cf49635c59ae0faf82044fd17bcc68a549ade6f95";
    final String raw = out.encoded().toHexString();
    final Transaction decoded = Transaction.readFrom(RLP.input(Bytes.fromHexString(raw)));
    System.out.println(decoded);
    /*System.out.println(decoded.getAccessList().orElseThrow().get(0).getAddress().toHexString());
    System.out.println(decoded.getAccessList().orElseThrow().get(0).getStorageKeys());*/
  }

  private static KeyPair keyPair(final String privateKey) {
    final SignatureAlgorithm signatureAlgorithm = SIGNATURE_ALGORITHM.get();
    return signatureAlgorithm.createKeyPair(
        signatureAlgorithm.createPrivateKey(Bytes32.fromHexString(privateKey)));
  }
}
