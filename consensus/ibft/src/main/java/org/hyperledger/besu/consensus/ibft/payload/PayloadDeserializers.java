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
package org.hyperledger.besu.consensus.ibft.payload;

import org.hyperledger.besu.consensus.common.bft.payload.Payload;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

public class PayloadDeserializers {

  public static SignedData<ProposalPayload> readSignedProposalPayloadFrom(final RLPInput rlpInput) {

    rlpInput.enterList();
    final ProposalPayload unsignedMessageData = ProposalPayload.readFrom(rlpInput);
    final SECPSignature signature = readSignature(rlpInput);
    rlpInput.leaveList();

    return from(unsignedMessageData, signature);
  }

  public static SignedData<PreparePayload> readSignedPreparePayloadFrom(final RLPInput rlpInput) {

    rlpInput.enterList();
    final PreparePayload unsignedMessageData = PreparePayload.readFrom(rlpInput);
    final SECPSignature signature = readSignature(rlpInput);
    rlpInput.leaveList();

    return from(unsignedMessageData, signature);
  }

  public static SignedData<CommitPayload> readSignedCommitPayloadFrom(final RLPInput rlpInput) {

    rlpInput.enterList();
    final CommitPayload unsignedMessageData = CommitPayload.readFrom(rlpInput);
    final SECPSignature signature = readSignature(rlpInput);
    rlpInput.leaveList();

    return from(unsignedMessageData, signature);
  }

  public static SignedData<RoundChangePayload> readSignedRoundChangePayloadFrom(
      final RLPInput rlpInput) {

    rlpInput.enterList();
    final RoundChangePayload unsignedMessageData = RoundChangePayload.readFrom(rlpInput);
    final SECPSignature signature = readSignature(rlpInput);
    rlpInput.leaveList();

    return from(unsignedMessageData, signature);
  }

  protected static <M extends Payload> SignedData<M> from(
      final M unsignedMessageData, final SECPSignature signature) {
    return SignedData.create(unsignedMessageData, signature);
  }

  protected static SECPSignature readSignature(final RLPInput signedMessage) {
    return signedMessage.readBytes(SignatureAlgorithmFactory.getInstance()::decodeSignature);
  }
}
