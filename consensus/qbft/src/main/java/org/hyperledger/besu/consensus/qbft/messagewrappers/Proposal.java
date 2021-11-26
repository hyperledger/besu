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
package org.hyperledger.besu.consensus.qbft.messagewrappers;

import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.messagewrappers.BftMessage;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.payload.ProposalPayload;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;

public class Proposal extends BftMessage<ProposalPayload> {

  private final List<SignedData<RoundChangePayload>> roundChanges;
  private final List<SignedData<PreparePayload>> prepares;

  public Proposal(
      final SignedData<ProposalPayload> payload,
      final List<SignedData<RoundChangePayload>> roundChanges,
      final List<SignedData<PreparePayload>> prepares) {
    super(payload);
    this.roundChanges = roundChanges;
    this.prepares = prepares;
  }

  public List<SignedData<RoundChangePayload>> getRoundChanges() {
    return roundChanges;
  }

  public List<SignedData<PreparePayload>> getPrepares() {
    return prepares;
  }

  public Block getBlock() {
    return getPayload().getProposedBlock();
  }

  @Override
  public Bytes encode() {
    final BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    rlpOut.startList();
    getSignedPayload().writeTo(rlpOut);

    rlpOut.startList();
    rlpOut.writeList(roundChanges, SignedData::writeTo);
    rlpOut.writeList(prepares, SignedData::writeTo);
    rlpOut.endList();

    rlpOut.endList();
    return rlpOut.encoded();
  }

  public static Proposal decode(final Bytes data, final BftExtraDataCodec bftExtraDataCodec) {
    final RLPInput rlpIn = RLP.input(data);
    rlpIn.enterList();
    final SignedData<ProposalPayload> payload =
        readPayload(rlpIn, rlpInput -> ProposalPayload.readFrom(rlpInput, bftExtraDataCodec));

    rlpIn.enterList();
    final List<SignedData<RoundChangePayload>> roundChanges =
        rlpIn.readList(r -> readPayload(r, RoundChangePayload::readFrom));
    final List<SignedData<PreparePayload>> prepares =
        rlpIn.readList(r -> readPayload(r, PreparePayload::readFrom));
    rlpIn.leaveList();

    rlpIn.leaveList();
    return new Proposal(payload, roundChanges, prepares);
  }
}
