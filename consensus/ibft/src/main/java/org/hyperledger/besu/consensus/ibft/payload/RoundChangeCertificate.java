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

import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.ibft.messagewrappers.RoundChange;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

public class RoundChangeCertificate {

  private final List<SignedData<RoundChangePayload>> roundChangePayloads;

  public RoundChangeCertificate(final List<SignedData<RoundChangePayload>> roundChangePayloads) {
    this.roundChangePayloads = roundChangePayloads;
  }

  public static RoundChangeCertificate readFrom(final RLPInput rlpInput) {
    final List<SignedData<RoundChangePayload>> roundChangePayloads;

    rlpInput.enterList();
    roundChangePayloads = rlpInput.readList(PayloadDeserializers::readSignedRoundChangePayloadFrom);
    rlpInput.leaveList();

    return new RoundChangeCertificate(roundChangePayloads);
  }

  public void writeTo(final RLPOutput rlpOutput) {
    rlpOutput.startList();
    rlpOutput.writeList(roundChangePayloads, SignedData::writeTo);
    rlpOutput.endList();
  }

  public Collection<SignedData<RoundChangePayload>> getRoundChangePayloads() {
    return roundChangePayloads;
  }

  public static class Builder {

    private final List<RoundChange> roundChangePayloads = Lists.newArrayList();

    public Builder() {}

    public void appendRoundChangeMessage(final RoundChange msg) {
      roundChangePayloads.add(msg);
    }

    public RoundChangeCertificate buildCertificate() {
      return new RoundChangeCertificate(
          roundChangePayloads.stream()
              .map(RoundChange::getSignedPayload)
              .collect(Collectors.toList()));
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RoundChangeCertificate that = (RoundChangeCertificate) o;
    return Objects.equals(roundChangePayloads, that.roundChangePayloads);
  }

  @Override
  public int hashCode() {
    return Objects.hash(roundChangePayloads);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", RoundChangeCertificate.class.getSimpleName() + "[", "]")
        .add("roundChangePayloads=" + roundChangePayloads)
        .toString();
  }
}
