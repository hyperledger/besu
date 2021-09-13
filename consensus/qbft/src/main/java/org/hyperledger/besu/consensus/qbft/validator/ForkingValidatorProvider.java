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

import static org.hyperledger.besu.config.QbftFork.VALIDATOR_SELECTION_MODE.BLOCKHEADER;
import static org.hyperledger.besu.config.QbftFork.VALIDATOR_SELECTION_MODE.CONTRACT;

import org.hyperledger.besu.config.QbftFork.VALIDATOR_SELECTION_MODE;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.VoteProvider;
import org.hyperledger.besu.consensus.common.validator.blockbased.BlockValidatorProvider;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

public class ForkingValidatorProvider implements ValidatorProvider {

  private final Blockchain blockchain;
  private final ValidatorSelectorForksSchedule forksSchedule;
  private final BlockValidatorProvider blockValidatorProvider;
  private final TransactionValidatorProvider transactionValidatorProvider;

  public ForkingValidatorProvider(
      final Blockchain blockchain,
      final ValidatorSelectorForksSchedule forksSchedule,
      final BlockValidatorProvider blockValidatorProvider,
      final TransactionValidatorProvider transactionValidatorProvider) {
    this.blockchain = blockchain;
    this.forksSchedule = forksSchedule;
    this.blockValidatorProvider = blockValidatorProvider;
    this.transactionValidatorProvider = transactionValidatorProvider;
  }

  @Override
  public Collection<Address> getValidatorsAtHead() {
    final BlockHeader header = blockchain.getChainHeadHeader();
    return getValidators(header.getNumber(), ValidatorProvider::getValidatorsAtHead);
  }

  @Override
  public Collection<Address> getValidatorsAfterBlock(final BlockHeader header) {
    return getValidators(header.getNumber(), p -> p.getValidatorsAfterBlock(header));
  }

  @Override
  public Collection<Address> getValidatorsForBlock(final BlockHeader header) {
    return getValidators(header.getNumber(), p -> p.getValidatorsForBlock(header));
  }

  @Override
  public Optional<VoteProvider> getVoteProvider() {
    return resolveValidatorProvider(blockchain.getChainHeadHeader().getNumber()).getVoteProvider();
  }

  private Collection<Address> getValidators(
      final long block, final Function<ValidatorProvider, Collection<Address>> getValidators) {
    final Optional<ValidatorSelectorConfig> fork = forksSchedule.getFork(block);
    final ValidatorProvider validatorProvider = resolveValidatorProvider(block);

    if (fork.isPresent()) {
      final VALIDATOR_SELECTION_MODE validatorSelectionMode =
          fork.get().getValidatorSelectionMode();

      // when moving to a block validator the first block needs to be initialised or created with
      // the previous block state otherwise we would have no validators
      if (validatorSelectionMode.equals(BLOCKHEADER)) {
        if (block > 0 && block == fork.get().getBlock()) {
          final long prevBlockNumber = block - 1L;
          final Optional<BlockHeader> prevBlockHeader = blockchain.getBlockHeader(prevBlockNumber);
          if (prevBlockHeader.isPresent()) {
            return resolveValidatorProvider(prevBlockNumber)
                .getValidatorsForBlock(prevBlockHeader.get());
          }
        }
        return getValidators.apply(validatorProvider);
      }
    }

    return getValidators.apply(validatorProvider);
  }

  private ValidatorProvider resolveValidatorProvider(final long block) {
    final Optional<ValidatorSelectorConfig> fork = forksSchedule.getFork(block);
    if (fork.isPresent()) {
      final VALIDATOR_SELECTION_MODE validatorSelectionMode =
          fork.get().getValidatorSelectionMode();
      if (validatorSelectionMode.equals(BLOCKHEADER)) {
        return blockValidatorProvider;
      }
      if (validatorSelectionMode.equals(CONTRACT) && fork.get().getContractAddress().isPresent()) {
        return transactionValidatorProvider;
      } else if (block > 0) { // if no contract address then resolve using previous block
        return resolveValidatorProvider(block - 1L);
      }
    }

    throw new IllegalStateException("Unknown qbft validator selection mode");
  }
}
