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

import tech.pegasys.pantheon.consensus.common.ValidatorVote;
import tech.pegasys.pantheon.consensus.common.VoteBlockInterface;
import tech.pegasys.pantheon.consensus.common.VoteType;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;

import java.util.List;
import java.util.Optional;

public class IbftVotingBlockInterface implements VoteBlockInterface {

  @Override
  public Optional<ValidatorVote> extractVoteFromHeader(final BlockHeader header) {
    final IbftExtraData ibftExtraData = IbftExtraData.decode(header.getExtraData());

    if (ibftExtraData.getVote().isPresent()) {
      final Vote headerVote = ibftExtraData.getVote().get();
      final ValidatorVote vote =
          new ValidatorVote(
              headerVote.isAuth() ? VoteType.ADD : VoteType.DROP,
              header.getCoinbase(),
              headerVote.getRecipient());
      return Optional.of(vote);
    }
    return Optional.empty();
  }

  @Override
  public List<Address> validatorsInBlock(final BlockHeader header) {
    final IbftExtraData ibftExtraData = IbftExtraData.decode(header.getExtraData());
    return ibftExtraData.getValidators();
  }
}
