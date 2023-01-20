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
package org.hyperledger.besu.consensus.common.validator.blockbased;

import org.hyperledger.besu.consensus.common.BftValidatorOverrides;
import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.VoteProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/** The Block validator provider. */
public class BlockValidatorProvider implements ValidatorProvider {

  private final VoteTallyCache voteTallyCache;
  private final VoteProvider voteProvider;
  private final BlockInterface blockInterface;

  /**
   * Forking validator provider block validator provider.
   *
   * @param blockchain the blockchain
   * @param epochManager the epoch manager
   * @param blockInterface the block interface
   * @param bftValidatorOverrides the bft validator overrides
   * @return the block validator provider
   */
  public static BlockValidatorProvider forkingValidatorProvider(
      final Blockchain blockchain,
      final EpochManager epochManager,
      final BlockInterface blockInterface,
      final BftValidatorOverrides bftValidatorOverrides) {
    return new BlockValidatorProvider(
        blockchain, epochManager, blockInterface, Optional.of(bftValidatorOverrides));
  }

  /**
   * Non forking validator provider block validator provider.
   *
   * @param blockchain the blockchain
   * @param epochManager the epoch manager
   * @param blockInterface the block interface
   * @return the block validator provider
   */
  public static BlockValidatorProvider nonForkingValidatorProvider(
      final Blockchain blockchain,
      final EpochManager epochManager,
      final BlockInterface blockInterface) {
    return new BlockValidatorProvider(blockchain, epochManager, blockInterface, Optional.empty());
  }

  private BlockValidatorProvider(
      final Blockchain blockchain,
      final EpochManager epochManager,
      final BlockInterface blockInterface,
      final Optional<BftValidatorOverrides> bftValidatorOverrides) {
    final VoteTallyUpdater voteTallyUpdater = new VoteTallyUpdater(epochManager, blockInterface);
    final VoteProposer voteProposer = new VoteProposer();
    this.voteTallyCache =
        bftValidatorOverrides.isPresent()
            ? new ForkingVoteTallyCache(
                blockchain,
                voteTallyUpdater,
                epochManager,
                blockInterface,
                bftValidatorOverrides.get())
            : new VoteTallyCache(blockchain, voteTallyUpdater, epochManager, blockInterface);
    this.voteProvider = new BlockVoteProvider(voteTallyCache, voteProposer);
    this.blockInterface = blockInterface;
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
  public Collection<Address> getValidatorsForBlock(final BlockHeader header) {
    return blockInterface.validatorsInBlock(header).stream().sorted().collect(Collectors.toList());
  }

  @Override
  public Optional<VoteProvider> getVoteProviderAtHead() {
    return Optional.of(voteProvider);
  }
}
