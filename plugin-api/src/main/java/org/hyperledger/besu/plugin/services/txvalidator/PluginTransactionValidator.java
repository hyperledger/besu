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

package org.hyperledger.besu.plugin.services.txvalidator;

import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.Unstable;

import java.util.Optional;

/** Interface for the transaction validator plugin */
@Unstable
public interface PluginTransactionValidator {

  /**
   * Method called to decide whether a transaction can be added to the transaction pool.
   *
   * @param transaction candidate transaction
   * @return Optional.empty() if the transaction is valid, an Optional containing an error message, if not
   */
  Optional<String> validateTransaction(final Transaction transaction);
}
