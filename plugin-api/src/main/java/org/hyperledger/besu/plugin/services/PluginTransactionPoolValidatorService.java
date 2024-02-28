/*
 * Copyright Hyperledger Besu Contributors.
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

package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolTransactionValidator;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidatorFactory;

/** Transaction validator for addition of transactions to the transaction pool */
@Unstable
public interface PluginTransactionPoolValidatorService extends BesuService {

  /**
   * Returns the transaction validator to be used in the txpool
   *
   * @return the transaction validator
   */
  PluginTransactionPoolTransactionValidator createTransactionValidator();

  /**
   * Registers the transaction validator factory with the service
   *
   * @param transactionValidatorFactory transaction validator factory to be used
   */
  void registerTransactionValidatorFactory(
      PluginTransactionPoolValidatorFactory transactionValidatorFactory);
}
