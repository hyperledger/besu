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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.SendRawTransactionConditionalParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.evm.internal.StorageEntry;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.units.bigints.UInt256;
import org.jetbrains.annotations.NotNull;

public class EthSendRawTransactionConditional extends AbstractEthSendRawTransaction {
  protected final Supplier<BlockchainQueries> blockchainQueries;

  public EthSendRawTransactionConditional(
      final BlockchainQueries blockchainQueries, final TransactionPool transactionPool) {
    this(Suppliers.ofInstance(blockchainQueries), Suppliers.ofInstance(transactionPool), false);
  }

  public EthSendRawTransactionConditional(
      final Supplier<BlockchainQueries> blockchainQueries,
      final Supplier<TransactionPool> transactionPool,
      final boolean sendEmptyHashOnInvalidBlock) {
    super(transactionPool, sendEmptyHashOnInvalidBlock);
    this.blockchainQueries = blockchainQueries;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_SEND_RAW_TRANSACTION_CONDITIONAL.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    if (requestContext.getRequest().getParamLength() < 1) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }
    final Optional<JsonRpcErrorResponse> errorResponse = checkConditions(requestContext);
    if (errorResponse.isEmpty()) {
      return processRawTransaction(requestContext);
    } else {
      return errorResponse.get();
    }
  }

  private Optional<JsonRpcErrorResponse> checkConditions(
      final JsonRpcRequestContext requestContext) {
    if (requestContext.getRequest().getParamLength() == 2) {
      final SendRawTransactionConditionalParameter conditions =
          conditionalParameter(requestContext);
      if (!withinRange(
          blockchainQueries.get().headBlockNumber(),
          conditions.getBlockNumberMin(),
          conditions.getBlockNumberMax())) {
        return getJsonRpcErrorResponse(requestContext, "block number not within specified range");
      }
      if (!withinRange(
          blockchainQueries.get().headBlockHeader().getTimestamp(),
          conditions.getTimestampMin(),
          conditions.getTimestampMax())) {
        return getJsonRpcErrorResponse(requestContext, "timestamp not within specified range");
      }
      if (conditions.getKnownAccounts().isEmpty()) {
        return Optional.empty();
      }
      final Map<Address, SendRawTransactionConditionalParameter.KnownAccountInfo> storageToCheck =
          conditions.getKnownAccounts().get();
      final long headBlockNumber = blockchainQueries.get().headBlockNumber();
      for (Address a : storageToCheck.keySet()) {
        SendRawTransactionConditionalParameter.KnownAccountInfo info = storageToCheck.get(a);
        if (info.getStorageRootHash().isPresent()) {
          // TODO check storage root
          // not yet exposed via blockchainQueries
        } else {
          for (StorageEntry expectedStorageEntry : info.getExpectedStorageEntries().get()) {
            final UInt256 slot = expectedStorageEntry.getOffset();
            final Optional<UInt256> actualStorageValue =
                blockchainQueries.get().storageAt(a, slot, headBlockNumber);
            if (actualStorageValue.isPresent()
                && !actualStorageValue.get().equals(expectedStorageEntry.getValue())) {
              return getJsonRpcErrorResponse(
                  requestContext,
                  String.format("storage at address %s slot %s has been modified", a, slot));
            }
          }
        }
      }
    }
    // no conditions violated - behave as eth_sendRawTransaction
    return Optional.empty();
  }

  private boolean withinRange(
      final long value, final Optional<Long> maybeMin, final Optional<Long> maybeMax) {
    long min = maybeMin.orElse(0L);
    long max = maybeMax.orElse(value);
    return value <= max && value >= min;
  }

  @NotNull
  private Optional<JsonRpcErrorResponse> getJsonRpcErrorResponse(
      final JsonRpcRequestContext requestContext, final String message) {
    final JsonRpcError jsonRpcError = JsonRpcError.USER_SPECIFIED_CONDITIONS_NOT_MET;
    jsonRpcError.setData(message);
    return Optional.of(new JsonRpcErrorResponse(requestContext.getRequest().getId(), jsonRpcError));
  }

  protected SendRawTransactionConditionalParameter conditionalParameter(
      final JsonRpcRequestContext request) {
    return request.getRequiredParameter(1, SendRawTransactionConditionalParameter.class);
  }
}
