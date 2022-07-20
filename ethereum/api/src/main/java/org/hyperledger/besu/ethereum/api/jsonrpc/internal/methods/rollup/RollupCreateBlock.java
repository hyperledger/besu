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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.rollup;

import org.hyperledger.besu.consensus.rollup.blockcreation.RollupMergeCoordinator;
import org.hyperledger.besu.consensus.rollup.blockcreation.RollupMergeCoordinator.PayloadCreationResult;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetPayloadResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.RollupCreateBlockResult;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator.BlockCreationResult;
import org.hyperledger.besu.ethereum.blockcreation.BlockTransactionSelector.TransactionSelectionResults;
import org.hyperledger.besu.ethereum.blockcreation.BlockTransactionSelector.TransactionValidationResult;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RollupCreateBlock extends ExecutionEngineJsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(RollupCreateBlock.class);
  private final RollupMergeCoordinator mergeCoordinator;
  private final BlockResultFactory blockResultFactory;

  public RollupCreateBlock(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final RollupMergeCoordinator mergeCoordinator,
      final BlockResultFactory blockResultFactory) {
    super(vertx, protocolContext);
    this.mergeCoordinator = mergeCoordinator;
    this.blockResultFactory = blockResultFactory;
  }

  @Override
  public String getName() {
    return RpcMethod.ROLLUP_CREATE_BLOCK.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    final Object requestId = requestContext.getRequest().getId();
    final Hash parentBlockHash;
    final List<Transaction> transactions;
    final Address suggestedRecipient;
    final long timestamp;
    final Bytes32 prevRandao;
    final List<?> rawTransactions;

    try {
      parentBlockHash = requestContext.getRequiredParameter(0, Hash.class);
      rawTransactions = requestContext.getRequiredParameter(1, List.class);
      prevRandao = requestContext.getRequiredParameter(2, Hash.class);
      suggestedRecipient = requestContext.getRequiredParameter(3, Address.class);
      timestamp = Long.decode(requestContext.getRequiredParameter(4, String.class));
      transactions =
          rawTransactions.stream()
              .map(Object::toString)
              .map(Bytes::fromHexString)
              .map(TransactionDecoder::decodeOpaqueBytes)
              .collect(Collectors.toList());
    } catch (final RLPException | IllegalArgumentException e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }
    final Optional<BlockHeader> parentBlock =
        protocolContext.getBlockchain().getBlockHeader(parentBlockHash);
    if (parentBlock.isEmpty()) {
      return replyWithStatus(requestId, RollupCreateBlockStatus.INVALID_TERMINAL_BLOCK);
    }

    try {
      final PayloadCreationResult result =
          mergeCoordinator.createBlock(
              parentBlock.get(), timestamp, suggestedRecipient, transactions, prevRandao);

      if (result.getBlockValidationResult().errorMessage.isPresent()) {
        return new JsonRpcSuccessResponse(
            requestId,
            new RollupCreateBlockResult(
                RollupCreateBlockStatus.INVALID_BLOCK,
                result.getBlockValidationResult().errorMessage));
      }

      final BlockCreationResult blockCreationResult = result.getBlockCreationResult();
      final EngineGetPayloadResult payloadResult =
          blockResultFactory.enginePayloadTransactionComplete(blockCreationResult.getBlock());

      final List<RollupCreateBlockResult.InvalidTransactionResult> invalidTransactionResults =
          invalidTransactionResults(
              rawTransactions, transactions, blockCreationResult.getTransactionSelectionResults());
      final List<String> unprocessedTransactions =
          unprocessedTransactions(
              rawTransactions,
              transactions,
              blockCreationResult.getBlock().getBody().getTransactions(),
              blockCreationResult.getTransactionSelectionResults().getInvalidTransactions().stream()
                  .map(TransactionValidationResult::getTransaction)
                  .collect(Collectors.toList()));

      return new JsonRpcSuccessResponse(
          requestId,
          new RollupCreateBlockResult(
              RollupCreateBlockStatus.PROCESSED,
              result.getPayloadId(),
              payloadResult,
              invalidTransactionResults,
              unprocessedTransactions));
    } catch (Exception e) {
      LOG.error("Failed to create block: ", e);
      return new JsonRpcErrorResponse(requestId, JsonRpcError.INTERNAL_ERROR);
    }
  }

  private ArrayList<String> unprocessedTransactions(
      final List<?> requestedTransactionsRaw,
      final List<Transaction> requestedTransactions,
      final List<Transaction> includedTransactions,
      final List<Transaction> invalidTransactions) {
    final ArrayList<String> unprocessedTransactions =
        new ArrayList<>(
            requestedTransactions.size()
                - includedTransactions.size()
                - includedTransactions.size());

    for (int i = 0; i < requestedTransactionsRaw.size(); i++) {
      Transaction tx = requestedTransactions.get(i);
      if (!includedTransactions.contains(tx) && !invalidTransactions.contains(tx)) {
        unprocessedTransactions.add((String) requestedTransactionsRaw.get(i));
      }
    }

    return unprocessedTransactions;
  }

  private List<RollupCreateBlockResult.InvalidTransactionResult> invalidTransactionResults(
      final List<?> requestedTransactionsRaw,
      final List<Transaction> requestedTransactions,
      final TransactionSelectionResults transactionSelectionResults) {

    return transactionSelectionResults.getInvalidTransactions().stream()
        .map(
            (TransactionValidationResult txValidation) -> {
              final var transactionRaw =
                  (String)
                      requestedTransactionsRaw.get(
                          requestedTransactions.indexOf(txValidation.getTransaction()));
              return new RollupCreateBlockResult.InvalidTransactionResult(
                  transactionRaw,
                  txValidation.getValidationResult().getInvalidReason(),
                  txValidation.getValidationResult().getErrorMessage());
            })
        .collect(Collectors.toList());
  }

  private JsonRpcResponse replyWithStatus(
      final Object requestId, final RollupCreateBlockStatus status) {
    return new JsonRpcSuccessResponse(
        requestId, new RollupCreateBlockResult(status, Optional.empty()));
  }
}
