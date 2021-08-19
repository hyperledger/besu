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

import static org.hyperledger.besu.config.QbftFork.VALIDATOR_MODE.BLOCKHEADER;
import static org.hyperledger.besu.config.QbftFork.VALIDATOR_MODE.CONTRACT;

import org.hyperledger.besu.config.QbftFork;
import org.hyperledger.besu.config.QbftFork.VALIDATOR_MODE;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.VoteProvider;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

// TODO-jf use this in the integration tests
public class ForkingValidatorProvider implements ValidatorProvider {

  private final Blockchain blockchain;
  private final QbftForksSchedule forksSchedule;
  private final ValidatorProviderFactory validatorProviderFactory;
  private final ValidatorProvider initialValidatorProvider;

  public ForkingValidatorProvider(
      final Blockchain blockchain,
      final QbftForksSchedule forksSchedule,
      final ValidatorProviderFactory validatorProviderFactory,
      final ValidatorProvider initialValidatorProvider) {
    this.blockchain = blockchain;
    this.forksSchedule = forksSchedule;
    this.validatorProviderFactory = validatorProviderFactory;
    this.initialValidatorProvider = initialValidatorProvider;
  }

  @Override
  public Collection<Address> getValidatorsAtHead() {
    final BlockHeader header = blockchain.getChainHeadHeader();
    return getValidators(header, ValidatorProvider::getValidatorsAtHead);
  }

  @Override
  public Collection<Address> getValidatorsAfterBlock(final BlockHeader header) {
    return getValidators(header, p -> p.getValidatorsAfterBlock(header));
  }

  @Override
  public Collection<Address> getValidatorsForBlock(final BlockHeader header) {
    return getValidators(header, p -> p.getValidatorsForBlock(header));
  }

  @Override
  public Optional<VoteProvider> getVoteProvider() {
    return resolveValidatorProvider(blockchain.getChainHeadHeader()).getVoteProvider();
  }

  private Collection<Address> getValidators(
      final BlockHeader header,
      final Function<ValidatorProvider, Collection<Address>> getValidators) {
    final Optional<QbftFork> fork = forksSchedule.getByBlockNumber(header.getNumber());
    final ValidatorProvider validatorProvider = resolveValidatorProvider(header);

    if (fork.isPresent() && fork.get().getValidatorSelectionMode().isPresent()) {
      final VALIDATOR_MODE validatorSelectionMode = fork.get().getValidatorSelectionMode().get();
      if (validatorSelectionMode.equals(BLOCKHEADER)) {
        if (header.getNumber() == fork.get().getForkBlock()) {
          final long prevBlockNumber = header.getNumber() == 0 ? 0 : header.getNumber() - 1L;
          final Optional<BlockHeader> prevBlockHeader = blockchain.getBlockHeader(prevBlockNumber);
          if (prevBlockHeader.isPresent()) {
            return getValidatorsForBlock(prevBlockHeader.get());
          }
        }
        return getValidators.apply(validatorProvider);
      }
    }

    return getValidators.apply(validatorProvider);
  }

  private ValidatorProvider resolveValidatorProvider(final BlockHeader header) {
    final Optional<QbftFork> fork = forksSchedule.getByBlockNumber(header.getNumber());
    if (fork.isPresent() && fork.get().getValidatorSelectionMode().isPresent()) {
      final VALIDATOR_MODE validatorSelectionMode = fork.get().getValidatorSelectionMode().get();
      if (validatorSelectionMode.equals(BLOCKHEADER)) {
        return validatorProviderFactory.createBlockValidatorProvider();
      } else if (validatorSelectionMode.equals(CONTRACT)
          && fork.get().getValidatorContractAddress().isPresent()) {
        final Address contractAddress =
            Address.fromHexString(fork.get().getValidatorContractAddress().get());
        return validatorProviderFactory.createTransactionValidatorProvider(contractAddress);
      }
    }

    return initialValidatorProvider;
  }
}
