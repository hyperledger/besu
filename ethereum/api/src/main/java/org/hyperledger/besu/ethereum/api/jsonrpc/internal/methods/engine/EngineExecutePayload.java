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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.ExecutionStatus.INVALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.ExecutionStatus.SYNCING;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.ExecutionStatus.VALID;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.ExecutionPayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineExecutionResult;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineExecutePayload extends ExecutionEngineJsonRpcMethod {

  private static final Hash OMMERS_HASH_CONSTANT = Hash.EMPTY_LIST_HASH;
  private static final Logger LOG = LoggerFactory.getLogger(EngineExecutePayload.class);
  private static final BlockHeaderFunctions headerFunctions = new MainnetBlockHeaderFunctions();
  private final MergeMiningCoordinator mergeCoordinator;

  public EngineExecutePayload(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator) {
    super(vertx, protocolContext);
    this.mergeCoordinator = mergeCoordinator;
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_EXECUTE_PAYLOAD.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    final ExecutionPayloadParameter blockParam =
        requestContext.getRequiredParameter(0, ExecutionPayloadParameter.class);

    Object reqId = requestContext.getRequest().getId();

    if (mergeContext.isSyncing()) {
      return respondWith(reqId, null, SYNCING, null);
    }

    // we already have this payload
    if (protocolContext.getBlockchain().getBlockByHash(blockParam.getBlockHash()).isPresent()) {
      LOG.debug("block already present");
      return respondWith(reqId, blockParam.getBlockHash(), VALID, null);
    }

    if (LOG.isTraceEnabled()) {
      LOG.trace("blockparam: {}", Json.encodePrettily(blockParam));
    }

    final List<Transaction> transactions;
    try {
      transactions =
          blockParam.getTransactions().stream()
              .map(Bytes::fromHexString)
              .map(TransactionDecoder::decodeOpaqueBytes)
              .collect(Collectors.toList());
    } catch (final RLPException | IllegalArgumentException e) {
      LOG.warn("failed to decode transactions from newBlock RPC", e);
      return respondWith(
          reqId,
          mergeCoordinator.getLatestValidAncestor(blockParam.getParentHash()).orElse(null),
          INVALID,
          "Failed to decode transactions from block parameter");
    }

    final BlockHeader newBlockHeader =
        new BlockHeader(
            blockParam.getParentHash(),
            OMMERS_HASH_CONSTANT,
            blockParam.getFeeRecipient(),
            blockParam.getStateRoot(),
            BodyValidation.transactionsRoot(transactions),
            blockParam.getReceiptsRoot(),
            blockParam.getLogsBloom(),
            Difficulty.ZERO,
            blockParam.getBlockNumber(),
            blockParam.getGasLimit(),
            blockParam.getGasUsed(),
            blockParam.getTimestamp(),
            Bytes.fromHexString(blockParam.getExtraData()),
            blockParam.getBaseFeePerGas(),
            blockParam.getRandom(),
            0,
            headerFunctions);

    final var block =
        new Block(newBlockHeader, new BlockBody(transactions, Collections.emptyList()));
    final var latestValidAncestor = mergeCoordinator.getLatestValidAncestor(newBlockHeader);

    if (latestValidAncestor.isEmpty()) {
      return respondWith(reqId, null, SYNCING, null);
    }

    boolean execSuccess = false;
    String errorMessage = null;
    // ensure the block hash matches the blockParam hash
    if (newBlockHeader.getHash().equals(blockParam.getBlockHash())) {

      // execute block
      execSuccess = mergeCoordinator.executeBlock(block);
    } else {
      errorMessage =
          String.format(
              "Computed block hash %s does not match block hash parameter %s",
              newBlockHeader.getBlockHash(), blockParam.getBlockHash());
    }

    // return result response
    if (execSuccess) {
      return respondWith(reqId, newBlockHeader.getHash(), VALID, errorMessage);
    } else {
      return respondWith(reqId, latestValidAncestor.get(), INVALID, errorMessage);
    }
  }

  JsonRpcResponse respondWith(
      final Object requestId,
      final Hash blockHash,
      final ExecutionStatus status,
      final String errorMessage) {
    return new JsonRpcSuccessResponse(
        requestId, new EngineExecutionResult(status, blockHash, errorMessage));
  }
}
