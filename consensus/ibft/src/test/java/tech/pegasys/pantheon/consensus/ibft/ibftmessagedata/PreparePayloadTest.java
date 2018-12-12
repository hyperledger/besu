/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.ibft.ibftmessagedata;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.IbftV2;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import org.junit.Test;

public class PreparePayloadTest {

  @Test
  public void roundTripRlp() {
    final Hash digest = Hash.hash(BytesValue.of(1));
    final ConsensusRoundIdentifier expectedRoundIdentifier = new ConsensusRoundIdentifier(1, 1);
    final PreparePayload preparePayload = new PreparePayload(expectedRoundIdentifier, digest);
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    preparePayload.writeTo(rlpOutput);

    final RLPInput rlpInput = RLP.input(rlpOutput.encoded());
    final PreparePayload actualPreparePayload = PreparePayload.readFrom(rlpInput);

    final ConsensusRoundIdentifier actualConsensusRoundIdentifier =
        actualPreparePayload.getRoundIdentifier();
    final Hash actualDigest = actualPreparePayload.getDigest();
    assertThat(actualConsensusRoundIdentifier)
        .isEqualToComparingFieldByField(expectedRoundIdentifier);
    assertThat(actualDigest).isEqualTo(digest);
    assertThat(actualPreparePayload.getMessageType()).isEqualTo(IbftV2.PREPARE);
  }
}
