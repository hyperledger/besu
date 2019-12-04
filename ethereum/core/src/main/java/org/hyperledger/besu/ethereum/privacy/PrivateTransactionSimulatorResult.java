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
package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Objects;

public class PrivateTransactionSimulatorResult {
  private final PrivateTransaction transaction;
  private final PrivateTransactionProcessor.Result result;
  private final Hash resultingRootHash;

  public PrivateTransactionSimulatorResult(
      final PrivateTransaction transaction,
      final PrivateTransactionProcessor.Result result,
      final Hash resultingRootHash) {
    this.transaction = transaction;
    this.result = result;
    this.resultingRootHash = resultingRootHash;
  }

  public boolean isSuccessful() {
    return result.isSuccessful();
  }

  public long getGasEstimate() {
    return transaction.getGasLimit() - result.getGasRemaining();
  }

  public BytesValue getOutput() {
    return result.getOutput();
  }

  public ValidationResult<TransactionValidator.TransactionInvalidReason> getValidationResult() {
    return result.getValidationResult();
  }

  public TransactionProcessor.Result getResult() {
    return result;
  }

  public Hash getResultingRootHash() {
    return resultingRootHash;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PrivateTransactionSimulatorResult that = (PrivateTransactionSimulatorResult) o;
    return transaction.equals(that.transaction)
        && result.equals(that.result)
        && resultingRootHash.equals(that.resultingRootHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(transaction, result, resultingRootHash);
  }
}
