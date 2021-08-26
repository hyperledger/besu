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
package org.hyperledger.besu.consensus.qbt.support;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.messagewrappers.BftMessage;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Hash;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

public class PrepareMessage implements RlpTestCaseMessage {
  private final UnsignedPrepare unsignedPrepare;
  private final String signature;

  @JsonCreator
  public PrepareMessage(
      @JsonProperty("unsignedPrepare") final UnsignedPrepare unsignedPrepare,
      @JsonProperty("signature") final String signature) {
    this.unsignedPrepare = unsignedPrepare;
    this.signature = signature;
  }

  @Override
  public BftMessage<PreparePayload> fromRlp(final Bytes rlp) {
    return Prepare.decode(rlp);
  }

  @Override
  public BftMessage<PreparePayload> toBftMessage() {
    return new Prepare(toSignedPreparePayload(this));
  }

  public static SignedData<PreparePayload> toSignedPreparePayload(
      final PrepareMessage prepareMessage) {
    final UnsignedPrepare unsignedPrepare = prepareMessage.unsignedPrepare;
    return SignedData.create(
        new PreparePayload(
            new ConsensusRoundIdentifier(unsignedPrepare.sequence, unsignedPrepare.round),
            Hash.fromHexString(unsignedPrepare.digest)),
        SignatureAlgorithmFactory.getInstance()
            .decodeSignature(Bytes.fromHexString(prepareMessage.signature)));
  }

  public static class UnsignedPrepare {
    private final long sequence;
    private final int round;
    private final String digest;

    @JsonCreator
    public UnsignedPrepare(
        @JsonProperty("sequence") final long sequence,
        @JsonProperty("round") final int round,
        @JsonProperty("digest") final String digest) {
      this.sequence = sequence;
      this.round = round;
      this.digest = digest;
    }
  }
}
