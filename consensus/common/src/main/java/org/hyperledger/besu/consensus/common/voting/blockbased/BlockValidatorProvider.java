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
package org.hyperledger.besu.consensus.common.voting.blockbased;

import org.hyperledger.besu.consensus.common.BftValidatorOverrides;
import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.ValidatorProvider;
import org.hyperledger.besu.consensus.common.voting.ValidatorVote;
import org.hyperledger.besu.consensus.common.voting.VoteType;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BlockValidatorProvider implements ValidatorProvider {

  private final VoteTallyCache voteTallyCache;
  private final VoteProposer voteProposer;

  public static ValidatorProvider forkingValidatorProvider(
      final Blockchain blockchain,
      final EpochManager epochManager,
      final BlockInterface blockInterface,
      final Map<Long, List<Address>> validatorForkMap) {
    return new BlockValidatorProvider(blockchain, epochManager, blockInterface, validatorForkMap);
  }

  public static ValidatorProvider nonForkingValidatorProvider(
      final Blockchain blockchain,
      final EpochManager epochManager,
      final BlockInterface blockInterface) {
    return new BlockValidatorProvider(
        blockchain, epochManager, blockInterface, Collections.emptyMap());
  }

  private BlockValidatorProvider(
      final Blockchain blockchain,
      final EpochManager epochManager,
      final BlockInterface blockInterface,
      final Map<Long, List<Address>> validatorForkMap) {
    final VoteTallyUpdater voteTallyUpdater = new VoteTallyUpdater(epochManager, blockInterface);
    this.voteTallyCache =
        new ForkingVoteTallyCache(
            blockchain,
            voteTallyUpdater,
            epochManager,
            blockInterface,
            new BftValidatorOverrides(validatorForkMap));
    this.voteProposer = new VoteProposer();
  }

  @Override
  public Collection<Address> getValidatorsAtHead() {
    return voteTallyCache.getVoteTallyAtHead().getValidators();
  }

  @Override
  public Collection<Address> getValidatorsAfterBlock(final BlockHeader header) {
    return voteTallyCache.getVoteTallyAfterBlock(header).getValidators();
  }

  @Override
  public Optional<ValidatorVote> getVoteAfterBlock(
      final BlockHeader header, final Address localAddress) {
    final VoteTally voteTally = voteTallyCache.getVoteTallyAfterBlock(header);
    return voteProposer.getVote(localAddress, voteTally);
  }

  @Override
  public void auth(final Address address) {
    voteProposer.auth(address);
  }

  @Override
  public void drop(final Address address) {
    voteProposer.drop(address);
  }

  @Override
  public void discard(final Address address) {
    voteProposer.discard(address);
  }

  @Override
  public Map<Address, VoteType> getProposals() {
    return voteProposer.getProposals();
  }
}
