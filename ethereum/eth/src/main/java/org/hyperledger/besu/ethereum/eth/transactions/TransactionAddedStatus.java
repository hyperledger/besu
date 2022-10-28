/*
 * Copyright Besu contributors.
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
package org.hyperledger.besu.ethereum.eth.transactions;

import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.util.Optional;

public enum TransactionAddedStatus {
  ALREADY_KNOWN(TransactionInvalidReason.TRANSACTION_ALREADY_KNOWN),
  REJECTED_UNDERPRICED_REPLACEMENT(TransactionInvalidReason.TRANSACTION_REPLACEMENT_UNDERPRICED),
  NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER(TransactionInvalidReason.NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER),
  LOWER_NONCE_INVALID_TRANSACTION_KNOWN(
      TransactionInvalidReason.LOWER_NONCE_INVALID_TRANSACTION_EXISTS),
  ADDED();

  private final Optional<TransactionInvalidReason> invalidReason;

  TransactionAddedStatus() {
    this.invalidReason = Optional.empty();
  }

  TransactionAddedStatus(final TransactionInvalidReason invalidReason) {
    this.invalidReason = Optional.of(invalidReason);
  }

  public Optional<TransactionInvalidReason> getInvalidReason() {
    return invalidReason;
  }
}
