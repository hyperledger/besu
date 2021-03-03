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
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Util;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class PrepareTest {

  @Test
  public void canRoundTripAPrepareMessage() {
    final NodeKey nodeKey = NodeKeyUtils.generate();
    final Address addr = Util.publicKeyToAddress(nodeKey.getPublicKey());

    final PreparePayload preparePayload =
        new PreparePayload(new QbftConsensusRoundIdentifier(1, 1), Hash.ZERO);

    final SignedData<PreparePayload> signedPreparePayload =
        SignedData.create(preparePayload, nodeKey.sign(qbftHashForSignature(preparePayload)));

    final Prepare prepareMsg = new Prepare(signedPreparePayload);

    final Prepare decodedPrepare = Prepare.decode(prepareMsg.encode());

    assertThat(decodedPrepare.getMessageType()).isEqualTo(QbftV1.PREPARE);
    assertThat(decodedPrepare.getAuthor()).isEqualTo(addr);
    assertThat(decodedPrepare.getSignedPayload())
        .isEqualToComparingFieldByField(signedPreparePayload);
  }

  @Test
  public void testInterop() {
    final String input =
        "f867e30c80a0c0ad22d202fd84209d95dabf073162cd470370baf384814e63caaaf2fcbaf053b8419794f9facb6f9c6dcdd3792b461227457c07ab65bab5b11f08e154d845a9ce175c090b7af46a38ce5c2eedf3a6a55597f3ec68cff10b5fc798feb210006e919501";

    final Bytes inBytes = Bytes.fromHexString(input);

    final Prepare prep = Prepare.decode(inBytes);

    assertThat(prep.getAuthor())
        .isEqualTo(Address.fromHexString("0xf4bBfD32C11c9d63E9b4c77bB225810F840342df"));
    assertThat(prep.getDigest())
        .isEqualTo(
            Hash.fromHexString(
                "0xc0ad22d202fd84209d95dabf073162cd470370baf384814e63caaaf2fcbaf053"));
    assertThat(prep.getRoundIdentifier().getSequenceNumber()).isEqualTo(12);
    assertThat(prep.getRoundIdentifier().getRoundNumber()).isEqualTo(0);
  }
}
