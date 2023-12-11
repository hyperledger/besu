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
package org.hyperledger.besu.consensus.ibft.payload;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.ibft.messagedata.IbftV2;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

public class CommitPayloadTest {
  private static final ConsensusRoundIdentifier ROUND_IDENTIFIER =
      new ConsensusRoundIdentifier(0x1234567890ABCDEFL, 0xFEDCBA98);

  @Test
  public void roundTripRlp() {
    final SECPSignature signature =
        SignatureAlgorithmFactory.getInstance()
            .createSignature(BigInteger.ONE, BigInteger.TEN, (byte) 0);
    final Hash hash = Hash.fromHexStringLenient("0x8523ba6e7c5f59ae87");

    final CommitPayload expectedCommitPayload =
        new CommitPayload(ROUND_IDENTIFIER, hash, signature);
    final BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    expectedCommitPayload.writeTo(rlpOut);

    final RLPInput rlpInput = RLP.input(rlpOut.encoded());
    final CommitPayload commitPayload = CommitPayload.readFrom(rlpInput);
    assertThat(commitPayload.getRoundIdentifier()).isEqualTo(ROUND_IDENTIFIER);
    assertThat(commitPayload.getCommitSeal()).isEqualTo(signature);
    assertThat(commitPayload.getDigest()).isEqualTo(hash);
    assertThat(commitPayload.getMessageType()).isEqualTo(IbftV2.COMMIT);
  }
}
