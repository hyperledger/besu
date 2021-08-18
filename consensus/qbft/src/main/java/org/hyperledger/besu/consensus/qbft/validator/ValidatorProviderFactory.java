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
import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.blockbased.BlockValidatorProvider;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;

import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public class ValidatorProviderFactory {

  private final Blockchain blockchain;
  private final Supplier<ValidatorProvider> blockValidatorProvider;
  private final TransactionSimulator transactionSimulator;

  public ValidatorProviderFactory(
      final Blockchain blockchain,
      final EpochManager epochManager,
      final BlockInterface blockInterface,
      final BftValidatorOverrides bftValidatorOverrides,
      final TransactionSimulator transactionSimulator) {
    this.blockchain = blockchain;
    this.transactionSimulator = transactionSimulator;
    this.blockValidatorProvider =
        Suppliers.memoize(
            () ->
                BlockValidatorProvider.forkingValidatorProvider(
                    blockchain, epochManager, blockInterface, bftValidatorOverrides));
  }

  public ValidatorProvider createBlockValidatorProvider() {
    return blockValidatorProvider.get();
  }

  public ValidatorProvider createTransactionValidatorProvider(final Address contractAddress) {
    final ValidatorContractController validatorContractController =
        new ValidatorContractController(contractAddress, transactionSimulator);
    return new TransactionValidatorProvider(blockchain, validatorContractController);
  }
}
