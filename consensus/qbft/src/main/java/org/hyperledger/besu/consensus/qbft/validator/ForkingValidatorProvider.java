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

package org.hyperledger.besu.consensus.qbft.validator;

import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.consensus.common.bft.BftForkSpec;
import org.hyperledger.besu.consensus.common.bft.BftForksSchedule;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.VoteProvider;
import org.hyperledger.besu.consensus.common.validator.blockbased.BlockValidatorProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

public class ForkingValidatorProvider implements ValidatorProvider {

  private final Blockchain blockchain;
  private final BftForksSchedule<QbftConfigOptions> forksSchedule;
  private final BlockValidatorProvider blockValidatorProvider;
  private final TransactionValidatorProvider transactionValidatorProvider;

  public ForkingValidatorProvider(
      final Blockchain blockchain,
      final BftForksSchedule<QbftConfigOptions> forksSchedule,
      final BlockValidatorProvider blockValidatorProvider,
      final TransactionValidatorProvider transactionValidatorProvider) {
    this.blockchain = blockchain;
    this.forksSchedule = forksSchedule;
    this.blockValidatorProvider = blockValidatorProvider;
    this.transactionValidatorProvider = transactionValidatorProvider;
  }

  @Override
  public Collection<Address> getValidatorsAtHead() {
    final BlockHeader chainHead = blockchain.getChainHeadHeader();
    return getValidatorsAfterBlock(chainHead);
  }

  @Override
  public Collection<Address> getValidatorsAfterBlock(final BlockHeader parentHeader) {
    final long nextBlock = parentHeader.getNumber() + 1;
    final ValidatorProvider validatorProvider = resolveValidatorProvider(nextBlock);
    final BftForkSpec<QbftConfigOptions> forkSpec = forksSchedule.getFork(nextBlock);
    return getValidators(validatorProvider, forkSpec, p -> p.getValidatorsAfterBlock(parentHeader));
  }

  @Override
  public Collection<Address> getValidatorsForBlock(final BlockHeader header) {
    final ValidatorProvider validatorProvider = resolveValidatorProvider(header.getNumber());
    final BftForkSpec<QbftConfigOptions> forkSpec = forksSchedule.getFork(header.getNumber());
    return getValidators(validatorProvider, forkSpec, p -> p.getValidatorsForBlock(header));
  }

  @Override
  public Optional<VoteProvider> getVoteProviderAtHead() {
    return resolveValidatorProvider(blockchain.getChainHeadHeader().getNumber())
        .getVoteProviderAtHead();
  }

  @Override
  public Optional<VoteProvider> getVoteProviderAfterBlock(final BlockHeader header) {
    return resolveValidatorProvider(header.getNumber() + 1).getVoteProviderAtHead();
  }

  private Collection<Address> getValidators(
      final ValidatorProvider validatorProvider,
      final BftForkSpec<QbftConfigOptions> forkSpec,
      final Function<ValidatorProvider, Collection<Address>> getValidators) {

    // when moving to a block validator the first block needs to be initialised or created with
    // the previous block state otherwise we would have no validators
    if (forkSpec.getConfigOptions().isValidatorBlockHeaderMode()) {
      if (forkSpec.getBlock() > 0) {
        final long prevBlockNumber = forkSpec.getBlock() - 1L;
        final Optional<BlockHeader> prevBlockHeader = blockchain.getBlockHeader(prevBlockNumber);
        if (prevBlockHeader.isPresent()) {
          return resolveValidatorProvider(prevBlockNumber)
              .getValidatorsForBlock(prevBlockHeader.get());
        }
      }
      return getValidators.apply(validatorProvider);
    }

    return getValidators.apply(validatorProvider);
  }

  private ValidatorProvider resolveValidatorProvider(final long block) {
    final BftForkSpec<QbftConfigOptions> fork = forksSchedule.getFork(block);
    return fork.getConfigOptions().isValidatorContractMode()
        ? transactionValidatorProvider
        : blockValidatorProvider;
  }
}
