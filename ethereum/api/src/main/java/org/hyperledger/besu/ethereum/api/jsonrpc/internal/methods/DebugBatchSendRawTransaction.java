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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.util.DomainObjectDecodeUtils;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.Suppliers;

public class DebugBatchSendRawTransaction implements JsonRpcMethod {
  private final Supplier<TransactionPool> transactionPool;

  public DebugBatchSendRawTransaction(final TransactionPool transactionPool) {
    this(Suppliers.ofInstance(transactionPool));
  }

  public DebugBatchSendRawTransaction(final Supplier<TransactionPool> transactionPool) {
    this.transactionPool = transactionPool;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_BATCH_RAW_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final List<ExecutionStatus> executionStatuses = new ArrayList<>();
    IntStream.range(0, requestContext.getRequest().getParamLength())
        .forEach(
            i -> {
              try {
                executionStatuses.add(
                    process(i, requestContext.getRequiredParameter(i, String.class)));
              } catch (JsonRpcParameterException e) {
                throw new InvalidJsonRpcParameters(
                    "Invalid parameter (index " + i + ")", RpcErrorType.INVALID_PARAMS, e);
              }
            });

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), executionStatuses);
  }

  private ExecutionStatus process(final int index, final String rawTransaction) {
    try {
      final ValidationResult<TransactionInvalidReason> validationResult =
          transactionPool
              .get()
              .addTransactionViaApi(DomainObjectDecodeUtils.decodeRawTransaction(rawTransaction));
      return validationResult.either(
          () -> new ExecutionStatus(index),
          errorReason -> new ExecutionStatus(index, false, errorReason.name()));
    } catch (final Throwable e) {
      return new ExecutionStatus(index, false, e.getMessage());
    }
  }

  @JsonPropertyOrder({"index", "success", "errorMessage"})
  static class ExecutionStatus {
    private final int index;
    private final boolean success;
    private final String errorMessage;

    ExecutionStatus(final int index) {
      this(index, true, null);
    }

    ExecutionStatus(final int index, final boolean success, final String errorMessage) {
      this.index = index;
      this.success = success;
      this.errorMessage = errorMessage;
    }

    @JsonGetter(value = "index")
    public int getIndex() {
      return index;
    }

    @JsonGetter(value = "success")
    public boolean isSuccess() {
      return success;
    }

    @JsonGetter(value = "errorMessage")
    @JsonInclude(Include.NON_NULL)
    public String getErrorMessage() {
      return errorMessage;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ExecutionStatus other = (ExecutionStatus) o;
      return index == other.index
          && success == other.success
          && Objects.equals(errorMessage, other.errorMessage);
    }

    @Override
    public int hashCode() {
      return Objects.hash(index, success, errorMessage);
    }
  }
}
