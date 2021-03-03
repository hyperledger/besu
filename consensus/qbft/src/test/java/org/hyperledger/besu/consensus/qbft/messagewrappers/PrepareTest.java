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
import static org.hyperledger.besu.consensus.common.bft.payload.PayloadHelpers.hashForSignature;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagedata.QbftV1;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Util;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.junit.Test;

public class PrepareTest {

  @Test
  public void canRoundTripAPrepareMessage() {
    final NodeKey nodeKey = NodeKeyUtils.generate();
    final Address addr = Util.publicKeyToAddress(nodeKey.getPublicKey());

    final PreparePayload preparePayload =
        new PreparePayload(new ConsensusRoundIdentifier(1, 1), Hash.ZERO);

    final SignedData<PreparePayload> signedPreparePayload =
        SignedData.create(preparePayload, nodeKey.sign(hashForSignature(preparePayload)));

    final Prepare prepareMsg = new Prepare(signedPreparePayload);

    final Prepare decodedPrepare = Prepare.decode(prepareMsg.encode());

    assertThat(decodedPrepare.getMessageType()).isEqualTo(QbftV1.PREPARE);
    assertThat(decodedPrepare.getAuthor()).isEqualTo(addr);
    assertThat(decodedPrepare.getSignedPayload())
        .isEqualToComparingFieldByField(signedPreparePayload);
  }

  @Test
  public void fromQuorumEncodedPacket() {
    final Bytes inputBytes = Bytes.wrap(new byte[]{
        (byte) 248, (byte) 103, (byte) 227, (byte) 29, (byte) 128, (byte) 160, (byte) 155,
        (byte) 198, (byte) 239, (byte) 162, (byte) 93, (byte) 83, (byte) 251, (byte) 83, (byte) 188,
        (byte) 248, (byte) 187, (byte) 241, (byte) 15, (byte) 106, (byte) 35, (byte) 190,
        (byte) 136, (byte) 11, (byte) 81, (byte) 221, (byte) 34, (byte) 213, (byte) 230, (byte) 184,
        (byte) 204, (byte) 122, (byte) 69, (byte) 218, (byte) 160, (byte) 63, (byte) 91, (byte) 113,
        (byte) 184, (byte) 65, (byte) 170, (byte) 226, (byte) 55, (byte) 233, (byte) 236, (byte) 62,
        (byte) 75, (byte) 195, (byte) 127, (byte) 238, (byte) 193, (byte) 147, (byte) 252, (byte) 2,
        (byte) 188, (byte) 117, (byte) 190, (byte) 174, (byte) 118, (byte) 32, (byte) 100,
        (byte) 21, (byte) 154, (byte) 193, (byte) 255, (byte) 51, (byte) 126, (byte) 220,
        (byte) 119, (byte) 233, (byte) 159, (byte) 36, (byte) 124, (byte) 182, (byte) 236,
        (byte) 169, (byte) 90, (byte) 189, (byte) 33, (byte) 62, (byte) 126, (byte) 64, (byte) 213,
        (byte) 129, (byte) 18, (byte) 36, (byte) 216, (byte) 6, (byte) 204, (byte) 251, (byte) 26,
        (byte) 58, (byte) 67, (byte) 8, (byte) 31, (byte) 16, (byte) 164, (byte) 198, (byte) 91,
        (byte) 9, (byte) 94, (byte) 35, (byte) 30, (byte) 38, (byte) 0
    });
    //final BytesValueRLPInput output = new BytesValueRLPInput(inputBytes, false);

    final Prepare msg = Prepare.decode(inputBytes);
  }




}
