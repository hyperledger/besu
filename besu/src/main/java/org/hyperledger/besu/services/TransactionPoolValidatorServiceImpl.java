/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.services;

import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.plugin.data.ValidationResult;
import org.hyperledger.besu.plugin.services.TransactionPoolValidatorService;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidator;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidatorFactory;

import java.util.Optional;

/** The Transaction pool validator service implementation. */
public class TransactionPoolValidatorServiceImpl implements TransactionPoolValidatorService {
  private TransactionPool transactionPool;

  private Optional<PluginTransactionPoolValidatorFactory> factory = Optional.empty();

  /** Default Constructor. */
  public TransactionPoolValidatorServiceImpl() {}

  /**
   * Set the transaction pool used for transactions validation
   *
   * @param transactionPool the transaction pool
   */
  public void init(final TransactionPool transactionPool) {
    this.transactionPool = transactionPool;
  }

  @Override
  public PluginTransactionPoolValidator createTransactionValidator() {
    return factory
        .map(PluginTransactionPoolValidatorFactory::createTransactionValidator)
        .orElse(PluginTransactionPoolValidator.VALIDATE_ALL);
  }

  @Override
  public void registerPluginTransactionValidatorFactory(
      final PluginTransactionPoolValidatorFactory pluginTransactionPoolValidatorFactory) {
    factory = Optional.ofNullable(pluginTransactionPoolValidatorFactory);
  }

  @Override
  public ValidationResult validateTransaction(
      final Transaction transaction, final boolean isLocal, final boolean hasPriority) {
    final var tx =
        transaction instanceof org.hyperledger.besu.ethereum.core.Transaction coreTx
            ? coreTx
            : new org.hyperledger.besu.ethereum.core.Transaction.Builder()
                .copiedFrom(transaction)
                .build();

    return transactionPool.validateTransaction(tx, isLocal, hasPriority);
  }
}
