/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.consensus.qbft.messagewrappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.common.bft.payload.PayloadHelpers.qbftHashForSignature;

import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.QbftConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.qbft.messagedata.QbftV1;
import org.hyperledger.besu.consensus.qbft.payload.CommitPayload;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Util;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class CommitTest {

  @Test
  public void canRoundTripACommitMessage() {
    final NodeKey nodeKey = NodeKeyUtils.generate();
    final Address addr = Util.publicKeyToAddress(nodeKey.getPublicKey());

    final CommitPayload commitPayload =
        new CommitPayload(
            new QbftConsensusRoundIdentifier(1, 1),
            Hash.ZERO,
            SignatureAlgorithmFactory.getInstance()
                .createSignature(BigInteger.ONE, BigInteger.ONE, (byte) 0));

    final SignedData<CommitPayload> signedCommitPayload =
        SignedData.create(commitPayload, nodeKey.sign(qbftHashForSignature(commitPayload)));

    final Commit commitMsg = new Commit(signedCommitPayload);

    final Commit decodedPrepare = Commit.decode(commitMsg.encode());

    assertThat(decodedPrepare.getMessageType()).isEqualTo(QbftV1.COMMIT);
    assertThat(decodedPrepare.getAuthor()).isEqualTo(addr);
    assertThat(decodedPrepare.getSignedPayload())
        .isEqualToComparingFieldByField(signedCommitPayload);
  }

  @Test
  public void testInterop() {
    final String input =
        "f8abf8660c80a0c0ad22d202fd84209d95dabf073162cd470370baf384814e63caaaf2fcbaf053b8415a161d0cb9380c9cff739190666408c3fc4a7a84f930482288762c524a66209b13a30876a1c9c56284eefc95096dc07580f24b58e22a045faddc825867627b7300b84145d73ddfb07c38ea484801060fe8207ea2ddab6e4fc0b80497b8681ce94df2110eebcf015e902d9526e746c414a6df458a0f25489e446551f68beacfa354154f00";

    final Bytes inBytes = Bytes.fromHexString(input);

    final Commit commit = Commit.decode(inBytes);

    assertThat(commit.getAuthor())
        .isEqualTo(Address.fromHexString("0x9F66F8a0f0A6537e4A36AA1799673ea7AE97a166"));
    assertThat(commit.getDigest())
        .isEqualTo(
            Hash.fromHexString(
                "0xc0ad22d202fd84209d95dabf073162cd470370baf384814e63caaaf2fcbaf053"));
    assertThat(commit.getRoundIdentifier().getSequenceNumber()).isEqualTo(12);
    assertThat(commit.getRoundIdentifier().getRoundNumber()).isEqualTo(0);
  }
}
