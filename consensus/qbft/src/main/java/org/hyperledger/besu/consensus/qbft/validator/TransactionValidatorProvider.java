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

import org.hyperledger.besu.consensus.common.BftValidatorOverrides;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.VoteProvider;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class TransactionValidatorProvider implements ValidatorProvider {

  private final Blockchain blockchain;
  private final ValidatorContractController validatorContractController;
  private final BftValidatorOverrides validatorOverrides;
  private final Cache<Long, Collection<Address>> validatorCache =
      CacheBuilder.newBuilder().maximumSize(100).build();

  public TransactionValidatorProvider(
      final Blockchain blockchain,
      final ValidatorContractController validatorContractController,
      final BftValidatorOverrides validatorOverrides) {
    this.blockchain = blockchain;
    this.validatorContractController = validatorContractController;
    this.validatorOverrides = validatorOverrides;
  }

  @Override
  public Collection<Address> getValidatorsAtHead() {
    return getValidatorsAfterBlock(blockchain.getChainHeadHeader());
  }

  @Override
  public Collection<Address> getValidatorsAfterBlock(final BlockHeader header) {
    final long blockNumber = header.getNumber();
    try {
      return validatorOverrides
          .getForBlock(blockNumber + 1L)
          .orElse(
              validatorCache.get(
                  blockNumber, () -> validatorContractController.getValidators(blockNumber)));
    } catch (ExecutionException e) {
      throw new RuntimeException("Unable to determine a validators for the requested block.");
    }
  }

  @Override
  public Optional<VoteProvider> getVoteProvider() {
    return Optional.of(new NoOpTransactionVoteProvider());
  }
}
