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
package org.hyperledger.besu.consensus.ibft;

import org.hyperledger.besu.consensus.common.VoteType;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Objects;

import com.google.common.collect.ImmutableBiMap;

/**
 * This class is only used to serialise/deserialise BlockHeaders and should not appear in business
 * logic.
 */
public class Vote {
  private final Address recipient;
  private final VoteType voteType;

  public static final byte ADD_BYTE_VALUE = (byte) 0xFF;
  public static final byte DROP_BYTE_VALUE = (byte) 0x0L;

  private static final ImmutableBiMap<VoteType, Byte> voteToValue =
      ImmutableBiMap.of(
          VoteType.ADD, ADD_BYTE_VALUE,
          VoteType.DROP, DROP_BYTE_VALUE);

  public Vote(final Address recipient, final VoteType voteType) {
    this.recipient = recipient;
    this.voteType = voteType;
  }

  public static Vote authVote(final Address address) {
    return new Vote(address, VoteType.ADD);
  }

  public static Vote dropVote(final Address address) {
    return new Vote(address, VoteType.DROP);
  }

  public Address getRecipient() {
    return recipient;
  }

  public boolean isAuth() {
    return voteType.equals(VoteType.ADD);
  }

  public boolean isDrop() {
    return voteType.equals(VoteType.DROP);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Vote vote1 = (Vote) o;
    return recipient.equals(vote1.recipient) && voteType.equals(vote1.voteType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(recipient, voteType);
  }

  public void writeTo(final RLPOutput rlpOutput) {
    rlpOutput.startList();
    rlpOutput.writeBytesValue(recipient);
    rlpOutput.writeByte(voteToValue.get(voteType));
    rlpOutput.endList();
  }

  public static Vote readFrom(final RLPInput rlpInput) {

    rlpInput.enterList();
    final Address recipient = Address.readFrom(rlpInput);
    final VoteType vote = voteToValue.inverse().get(rlpInput.readByte());
    if (vote == null) {
      throw new RLPException("Vote field was of an incorrect binary value.");
    }
    rlpInput.leaveList();

    return new Vote(recipient, vote);
  }
}
