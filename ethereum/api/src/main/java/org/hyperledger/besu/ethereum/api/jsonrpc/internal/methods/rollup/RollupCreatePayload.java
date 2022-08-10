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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.RollupCreatePayloadResult;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator.BlockCreationResult;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RollupCreatePayload extends ExecutionEngineJsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(RollupCreatePayload.class);
  private final RollupMergeCoordinator mergeCoordinator;
  private final BlockResultFactory blockResultFactory;

  public RollupCreatePayload(
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
    return RpcMethod.ROLLUP_CREATE_PAYLOAD.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    final Object requestId = requestContext.getRequest().getId();
    final Hash parentBlockHash;
    final List<Transaction> transactions;
    final Address suggestedRecipient;
    final long timestamp;
    final Bytes32 prevRandao;
    final List<String> rawTransactions;

    try {
      parentBlockHash = requestContext.getRequiredParameter(0, Hash.class);
      rawTransactions =
          ((List<?>) requestContext.getRequiredParameter(1, List.class))
              .stream().map(Object::toString).collect(Collectors.toList());
      prevRandao = requestContext.getRequiredParameter(2, Hash.class);
      suggestedRecipient = requestContext.getRequiredParameter(3, Address.class);
      timestamp = Long.decode(requestContext.getRequiredParameter(4, String.class));
      transactions =
          rawTransactions.stream()
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
      return replyWithStatus(requestId, RollupCreatePayloadStatus.INVALID_TERMINAL_BLOCK);
    }

    try {
      final PayloadCreationResult result =
          mergeCoordinator.createBlock(
              parentBlock.get(), timestamp, suggestedRecipient, transactions, prevRandao);

      if (result.getBlockValidationResult().errorMessage.isPresent()) {
        return new JsonRpcSuccessResponse(
            requestId,
            new RollupCreatePayloadResult(
                RollupCreatePayloadStatus.INVALID_BLOCK,
                result.getBlockValidationResult().errorMessage));
      }

      final BlockCreationResult blockCreationResult = result.getBlockCreationResult();
      final EngineGetPayloadResult payloadResult =
          blockResultFactory.enginePayloadTransactionComplete(blockCreationResult.getBlock());

      return new JsonRpcSuccessResponse(
          requestId,
          new RollupCreatePayloadResult(
              RollupCreatePayloadStatus.PROCESSED,
              result.getPayloadId(),
              payloadResult,
              rawTransactions,
              transactions,
              blockCreationResult.getBlock().getBody().getTransactions(),
              blockCreationResult.getTransactionSelectionResults().getInvalidTransactions()));
    } catch (Exception e) {
      LOG.error("Failed to create block: ", e);
      return new JsonRpcErrorResponse(requestId, JsonRpcError.INTERNAL_ERROR);
    }
  }

  private JsonRpcResponse replyWithStatus(
      final Object requestId, final RollupCreatePayloadStatus status) {
    return new JsonRpcSuccessResponse(
        requestId, new RollupCreatePayloadResult(status, Optional.empty()));
  }
}
