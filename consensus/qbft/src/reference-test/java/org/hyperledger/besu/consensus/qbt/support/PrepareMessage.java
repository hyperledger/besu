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
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.Hash;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

public class PrepareMessage implements RlpTestInput {
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
  public RlpTestInput fromRlp(final Bytes rlp) {
    final Prepare prepare = Prepare.decode(rlp);
    return new PrepareMessage(
        new UnsignedPrepare(
            prepare.getRoundIdentifier().getSequenceNumber(),
            prepare.getRoundIdentifier().getRoundNumber(),
            prepare.getDigest().toHexString()),
        prepare.getSignedPayload().getSignature().encodedBytes().toHexString());
  }

  @Override
  public Bytes toRlp() {
    final PreparePayload preparePayload =
        new PreparePayload(
            new ConsensusRoundIdentifier(unsignedPrepare.sequence, unsignedPrepare.round),
            Hash.fromHexStringLenient(unsignedPrepare.digest));
    final SignedData<PreparePayload> signedPreparePayload =
        SignedData.create(preparePayload, Signature.decode(Bytes.fromHexString(signature)));
    return new Prepare(signedPreparePayload).encode();
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

    public long getSequence() {
      return sequence;
    }

    public int getRound() {
      return round;
    }

    public String getDigest() {
      return digest;
    }
  }

  // TODO dupe here, this is the same as PrepareMessage
  public static class SignedPrepare {
    private final UnsignedPrepare unsignedPrepare;
    private final String signature;

    @JsonCreator
    public SignedPrepare(
        @JsonProperty("unsignedPrepare") final UnsignedPrepare unsignedPrepare,
        @JsonProperty("signature") final String signature) {
      this.unsignedPrepare = unsignedPrepare;
      this.signature = signature;
    }

    public UnsignedPrepare getUnsignedPrepare() {
      return unsignedPrepare;
    }

    public String getSignature() {
      return signature;
    }
  }
}
