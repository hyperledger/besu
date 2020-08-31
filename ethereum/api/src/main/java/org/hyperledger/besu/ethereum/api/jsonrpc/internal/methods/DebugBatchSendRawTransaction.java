package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcRequestException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.util.DomainObjectDecodeUtils;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.ArrayList;
import java.util.List;
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
            i ->
                executionStatuses.add(
                    process(i, requestContext.getRequiredParameter(i, String.class))));

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), executionStatuses);
  }

  private ExecutionStatus process(final int index, final String rawTransaction) {
    try {
      final ValidationResult<TransactionValidator.TransactionInvalidReason> validationResult =
          transactionPool
              .get()
              .addLocalTransaction(DomainObjectDecodeUtils.decodeRawTransaction(rawTransaction));
      return validationResult.either(
          () -> new ExecutionStatus(index),
          errorReason -> new ExecutionStatus(index, false, errorReason.name()));
    } catch (final InvalidJsonRpcRequestException e) {
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
  }
}
