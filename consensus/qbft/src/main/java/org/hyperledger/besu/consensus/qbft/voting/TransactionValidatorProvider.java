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
package org.hyperledger.besu.consensus.qbft.voting;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.VoteProvider;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TransactionValidatorProvider implements ValidatorProvider {

  private final Blockchain blockchain;
  private final ValidatorSmartContractController validatorSmartContractController;
  // TODO cache results
  // TODO use fork map to override

  public TransactionValidatorProvider(
      final Blockchain blockchain,
      final ValidatorSmartContractController validatorSmartContractController,
      final Map<Long, List<Address>> bftValidatorForkMap) {
    this.blockchain = blockchain;
    this.validatorSmartContractController = validatorSmartContractController;
  }

  @Override
  public Collection<Address> getValidatorsAtHead() {
    return validatorSmartContractController.getValidators(blockchain.getChainHeadHeader());
  }

  @Override
  public Collection<Address> getValidatorsAfterBlock(final BlockHeader header) {
    return validatorSmartContractController.getValidators(header);
  }

  @Override
  public Optional<VoteProvider> getVoteProvider() {
    return Optional.of(new NoOpTransactionVoteProvider());
  }
}
