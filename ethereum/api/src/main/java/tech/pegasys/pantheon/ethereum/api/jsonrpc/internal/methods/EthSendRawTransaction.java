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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods;

import static tech.pegasys.pantheon.ethereum.api.jsonrpc.JsonRpcErrorConverter.convertTransactionInvalidReason;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcRequestException;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EthSendRawTransaction implements JsonRpcMethod {

  private static final Logger LOG = LogManager.getLogger();
  private final boolean sendEmptyHashOnInvalidBlock;

  private final Supplier<TransactionPool> transactionPool;
  private final JsonRpcParameter parameters;

  public EthSendRawTransaction(
      final TransactionPool transactionPool, final JsonRpcParameter parameters) {
    this(Suppliers.ofInstance(transactionPool), parameters, false);
  }

  public EthSendRawTransaction(
      final Supplier<TransactionPool> transactionPool,
      final JsonRpcParameter parameters,
      final boolean sendEmptyHashOnInvalidBlock) {
    this.transactionPool = transactionPool;
    this.parameters = parameters;
    this.sendEmptyHashOnInvalidBlock = sendEmptyHashOnInvalidBlock;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_SEND_RAW_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    if (request.getParamLength() != 1) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }
    final String rawTransaction = parameters.required(request.getParams(), 0, String.class);

    final Transaction transaction;
    try {
      transaction = decodeRawTransaction(rawTransaction);
    } catch (final InvalidJsonRpcRequestException e) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }

    final ValidationResult<TransactionInvalidReason> validationResult =
        transactionPool.get().addLocalTransaction(transaction);
    return validationResult.either(
        () -> new JsonRpcSuccessResponse(request.getId(), transaction.hash().toString()),
        errorReason ->
            sendEmptyHashOnInvalidBlock
                ? new JsonRpcSuccessResponse(request.getId(), Hash.EMPTY.toString())
                : new JsonRpcErrorResponse(
                    request.getId(), convertTransactionInvalidReason(errorReason)));
  }

  private Transaction decodeRawTransaction(final String hash)
      throws InvalidJsonRpcRequestException {
    try {
      return Transaction.readFrom(RLP.input(BytesValue.fromHexString(hash)));
    } catch (final IllegalArgumentException | RLPException e) {
      LOG.debug(e);
      throw new InvalidJsonRpcRequestException("Invalid raw transaction hex", e);
    }
  }
}
