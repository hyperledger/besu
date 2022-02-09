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
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockValidator.Result;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
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
    final EnginePayloadParameter blockParam =
        requestContext.getRequiredParameter(0, EnginePayloadParameter.class);

    Object reqId = requestContext.getRequest().getId();

    if (mergeContext.isSyncing()) {
      return respondWith(reqId, null, SYNCING, null);
    }

    traceLambda(LOG, "blockparam: {}", () -> Json.encodePrettily(blockParam));

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

    String errorMessage = null;

    // ensure the block hash matches the blockParam hash
    if (!newBlockHeader.getHash().equals(blockParam.getBlockHash())) {
      errorMessage =
          String.format(
              "Computed block hash %s does not match block hash parameter %s",
              newBlockHeader.getBlockHash(), blockParam.getBlockHash());
    } else {
      // do we already have this payload?
      if (protocolContext
          .getBlockchain()
          .getBlockByHash(newBlockHeader.getBlockHash())
          .isPresent()) {
        LOG.debug("block {} already present", newBlockHeader.getBlockHash());
        return respondWith(reqId, blockParam.getBlockHash(), VALID, null);
      }
    }

    final var latestValidAncestor = mergeCoordinator.getLatestValidAncestor(newBlockHeader);

    if (latestValidAncestor.isEmpty()) {
      return respondWith(reqId, null, SYNCING, null);
    }

    if (errorMessage != null) {
      return respondWith(reqId, latestValidAncestor.get(), INVALID, errorMessage);
    }

      // TODO: post-merge cleanup
    if (!mergeCoordinator.latestValidAncestorDescendsFromTerminal(newBlockHeader)) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_TERMINAL_BLOCK);
    }

    final var block =
        new Block(newBlockHeader, new BlockBody(transactions, Collections.emptyList()));

    // execute block and return result response
    Result result = mergeCoordinator.executeBlock(block);
    if (result.errorMessage.isEmpty()) {
      return respondWith(reqId, newBlockHeader.getHash(), VALID, null);
    } else {
      return respondWith(
          reqId, latestValidAncestor.get(), INVALID, result.errorMessage.orElse("Unknown reason"));
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
