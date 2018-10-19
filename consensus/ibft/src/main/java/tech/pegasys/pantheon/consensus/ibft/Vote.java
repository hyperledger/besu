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
package tech.pegasys.pantheon.consensus.ibft;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;

import com.google.common.base.Objects;

public class Vote {
  private final Address recipient;
  private final IbftVoteType voteType;

  private Vote(final Address recipient, final IbftVoteType voteType) {
    this.recipient = recipient;
    this.voteType = voteType;
  }

  public static Vote authVote(final Address address) {
    return new Vote(address, IbftVoteType.ADD);
  }

  public static Vote dropVote(final Address address) {
    return new Vote(address, IbftVoteType.DROP);
  }

  public Address getRecipient() {
    return recipient;
  }

  public boolean isAuth() {
    return voteType.equals(IbftVoteType.ADD);
  }

  public boolean isDrop() {
    return voteType.equals(IbftVoteType.DROP);
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
    return Objects.hashCode(recipient, voteType);
  }

  public IbftVoteType getVoteType() {
    return voteType;
  }

  public void writeTo(final RLPOutput rlpOutput) {
    rlpOutput.startList();
    rlpOutput.writeBytesValue(recipient);
    voteType.writeTo(rlpOutput);
    rlpOutput.endList();
  }

  public static Vote readFrom(final RLPInput rlpInput) {

    rlpInput.enterList();
    final Address recipient = Address.readFrom(rlpInput);
    final IbftVoteType vote = IbftVoteType.readFrom(rlpInput);
    rlpInput.leaveList();

    return new Vote(recipient, vote);
  }
}
