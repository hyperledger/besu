/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor;

import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionProcessor.Result;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import com.google.common.base.Objects;

public class TransientTransactionProcessingResult {

  private final Transaction transaction;
  private final Result result;

  TransientTransactionProcessingResult(final Transaction transaction, final Result result) {
    this.transaction = transaction;
    this.result = result;
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

  public ValidationResult<TransactionInvalidReason> getValidationResult() {
    return result.getValidationResult();
  }

  public Result getResult() {
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TransientTransactionProcessingResult that = (TransientTransactionProcessingResult) o;
    return Objects.equal(transaction, that.transaction) && Objects.equal(result, that.result);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(transaction, result);
  }
}
