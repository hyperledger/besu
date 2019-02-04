/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.consensus.ibft.messagewrappers;

import tech.pegasys.pantheon.consensus.ibft.payload.NewRoundPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public class NewRound extends IbftMessage<NewRoundPayload> {

  public NewRound(final SignedData<NewRoundPayload> payload) {
    super(payload);
  }

  public RoundChangeCertificate getRoundChangeCertificate() {
    return getPayload().getRoundChangeCertificate();
  }

  public SignedData<ProposalPayload> getProposalPayload() {
    return getPayload().getProposalPayload();
  }

  public Block getBlock() {
    return getProposalPayload().getPayload().getBlock();
  }

  @Override
  public BytesValue encode() {
    final BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    rlpOut.startList();
    getSignedPayload().writeTo(rlpOut);
    rlpOut.endList();
    return rlpOut.encoded();
  }

  public static NewRound decode(final BytesValue data) {
    RLPInput rlpIn = RLP.input(data);
    rlpIn.enterList();
    final SignedData<NewRoundPayload> payload = SignedData.readSignedNewRoundPayloadFrom(rlpIn);
    rlpIn.leaveList();
    return new NewRound(payload);
  }
}
