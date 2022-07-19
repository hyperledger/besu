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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.rollup.RollupCreateBlockStatus;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
  "status",
  "errorMessage",
  "payloadId",
  "executionPayload",
  "failedTransactions"
})
public class RollupCreateBlockResult {
  private final RollupCreateBlockStatus status;
  private final PayloadIdentifier payloadId;
  private final EngineGetPayloadResult executionPayload;
  private final List<InvalidTransactionResult> invalidTransactions;
  private final Optional<String> errorMessage;

  public RollupCreateBlockResult(
      final RollupCreateBlockStatus status,
      final PayloadIdentifier payloadId,
      final EngineGetPayloadResult executionPayload,
      final List<InvalidTransactionResult> invalidTransactions) {
    this.status = status;
    this.payloadId = payloadId;
    this.executionPayload = executionPayload;
    this.invalidTransactions = invalidTransactions;
    this.errorMessage = Optional.empty();
  }

  public RollupCreateBlockResult(
      final RollupCreateBlockStatus status, final Optional<String> errorMessage) {
    this.status = status;
    this.errorMessage = errorMessage;
    this.payloadId = null;
    this.executionPayload = null;
    this.invalidTransactions = null;
  }

  @JsonGetter(value = "status")
  public RollupCreateBlockStatus getStatus() {
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
}
