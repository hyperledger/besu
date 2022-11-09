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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.util.DomainObjectDecodeUtils;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EthSendRawTransaction implements JsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(EthSendRawTransaction.class);

  private final boolean sendEmptyHashOnInvalidBlock;

  private final Supplier<TransactionPool> transactionPool;

  public EthSendRawTransaction(final TransactionPool transactionPool) {
    this(Suppliers.ofInstance(transactionPool), false);
  }

  public EthSendRawTransaction(
      final Supplier<TransactionPool> transactionPool, final boolean sendEmptyHashOnInvalidBlock) {
    this.transactionPool = transactionPool;
    this.sendEmptyHashOnInvalidBlock = sendEmptyHashOnInvalidBlock;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_SEND_RAW_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    if (requestContext.getRequest().getParamLength() != 1) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }
    final String rawTransaction = requestContext.getRequiredParameter(0, String.class);

    final Transaction transaction;
    try {
      transaction = DomainObjectDecodeUtils.decodeRawTransaction(rawTransaction);
    } catch (final RLPException | IllegalArgumentException e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    LOG.trace("Received local transaction {}", transaction);

    final ValidationResult<TransactionInvalidReason> validationResult =
        transactionPool.get().addLocalTransaction(transaction);
    return validationResult.either(
        () ->
            new JsonRpcSuccessResponse(
                requestContext.getRequest().getId(), transaction.getHash().toString()),
        errorReason ->
            sendEmptyHashOnInvalidBlock
                ? new JsonRpcSuccessResponse(
                    requestContext.getRequest().getId(), Hash.EMPTY.toString())
                : new JsonRpcErrorResponse(
                    requestContext.getRequest().getId(),
                    JsonRpcErrorConverter.convertTransactionInvalidReason(errorReason)));
  }
}
