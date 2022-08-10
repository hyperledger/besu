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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.rollup.RollupCreatePayloadStatus;
import org.hyperledger.besu.ethereum.blockcreation.BlockTransactionSelector.TransactionValidationResult;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
  "status",
  "errorMessage",
  "payloadId",
  "executionPayload",
  "failedTransactions",
  "unprocessedTransactions"
})
@JsonInclude(Include.NON_NULL)
public class RollupCreatePayloadResult {

  private final RollupCreatePayloadStatus status;
  private final PayloadIdentifier payloadId;
  private final EngineGetPayloadResult executionPayload;
  private final List<InvalidTransactionResult> invalidTransactions;
  private final List<String> unprocessedTransactions;
  private final Optional<String> errorMessage;

  public RollupCreatePayloadResult(
      final RollupCreatePayloadStatus status,
      final PayloadIdentifier payloadId,
      final EngineGetPayloadResult executionPayload,
      final List<String> requestedTransactionsRaw,
      final List<Transaction> requestedTransactions,
      final List<Transaction> executedTransactions,
      final List<TransactionValidationResult> invalidTransactions) {

    this.status = status;
    this.payloadId = payloadId;
    this.executionPayload = executionPayload;
    this.invalidTransactions =
        invalidTransactionResults(
            requestedTransactionsRaw,
            requestedTransactions,
            executedTransactions,
            invalidTransactions);
    this.unprocessedTransactions =
        unprocessedTransactions(
            requestedTransactionsRaw,
            requestedTransactions,
            executedTransactions,
            invalidTransactions.stream()
                .map(TransactionValidationResult::getTransaction)
                .collect(Collectors.toList()));
    this.errorMessage = Optional.empty();
  }

  public RollupCreatePayloadResult(
      final RollupCreatePayloadStatus status, final Optional<String> errorMessage) {
    this.status = status;
    this.errorMessage = errorMessage;
    this.payloadId = null;
    this.executionPayload = null;
    this.invalidTransactions = null;
    this.unprocessedTransactions = null;
  }

  @JsonGetter(value = "status")
  public RollupCreatePayloadStatus getStatus() {
    return status;
  }

  @JsonGetter(value = "errorMessage")
  public String getErrorMessage() {
    return errorMessage.orElse(null);
  }

  @JsonGetter(value = "payloadId")
  public String getPayloadId() {
    return Optional.ofNullable(payloadId).map(PayloadIdentifier::toHexString).orElse(null);
  }

  @JsonGetter(value = "executionPayload")
  public EngineGetPayloadResult getExecutionPayload() {
    return executionPayload;
  }

  @JsonGetter(value = "invalidTransactions")
  public List<InvalidTransactionResult> getInvalidTransactions() {
    return invalidTransactions;
  }

  @JsonGetter(value = "unprocessedTransactions")
  public List<String> getUnprocessedTransactions() {
    return unprocessedTransactions;
  }

  public static class InvalidTransactionResult {

    private final String transaction;
    private final TransactionInvalidReason invalidReason;
    private final String errorMessage;

    public InvalidTransactionResult(
        final String transaction,
        final TransactionInvalidReason invalidReason,
        final String errorMessage) {
      this.transaction = transaction;
      this.invalidReason = invalidReason;
      this.errorMessage = errorMessage;
    }

    @JsonGetter(value = "transaction")
    public String getTransaction() {
      return transaction;
    }

    @JsonGetter(value = "reason")
    public TransactionInvalidReason getInvalidReason() {
      return invalidReason;
    }

    @JsonGetter(value = "errorMessage")
    public String getErrorMessage() {
      return errorMessage;
    }
  }

  static List<InvalidTransactionResult> invalidTransactionResults(
      final List<?> requestedTransactionsRaw,
      final List<Transaction> requestedTransactions,
      final List<Transaction> executedTransactions,
      final List<TransactionValidationResult> invalidTransactions) {

    return invalidTransactions.stream()
        .filter(
            (TransactionValidationResult txValidation) ->
                !executedTransactions.contains(txValidation.getTransaction()))
        .map(
            (TransactionValidationResult txValidation) -> {
              final var transactionRaw =
                  (String)
                      requestedTransactionsRaw.get(
                          requestedTransactions.indexOf(txValidation.getTransaction()));
              return new RollupCreatePayloadResult.InvalidTransactionResult(
                  transactionRaw,
                  txValidation.getValidationResult().getInvalidReason(),
                  txValidation.getValidationResult().getErrorMessage());
            })
        .collect(Collectors.toList());
  }

  static ArrayList<String> unprocessedTransactions(
      final List<?> requestedTransactionsRaw,
      final List<Transaction> requestedTransactions,
      final List<Transaction> executedTransactions,
      final List<Transaction> invalidTransactions) {
    final ArrayList<String> unprocessedTransactions =
        new ArrayList<>(
            requestedTransactions.size()
                - executedTransactions.size()
                - invalidTransactions.size());

    for (int i = 0; i < requestedTransactionsRaw.size(); i++) {
      Transaction tx = requestedTransactions.get(i);
      if (!executedTransactions.contains(tx) && !invalidTransactions.contains(tx)) {
        unprocessedTransactions.add((String) requestedTransactionsRaw.get(i));
      }
    }

    return unprocessedTransactions;
  }
}
