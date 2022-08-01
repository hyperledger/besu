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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.ACCEPTED;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID_BLOCK_HASH;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.SYNCING;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.VALID;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.warnLambda;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EnginePayloadStatusResult;
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
import java.util.Optional;
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineNewPayload extends ExecutionEngineJsonRpcMethod {

  private static final Hash OMMERS_HASH_CONSTANT = Hash.EMPTY_LIST_HASH;
  private static final Logger LOG = LoggerFactory.getLogger(EngineNewPayload.class);
  private static final BlockHeaderFunctions headerFunctions = new MainnetBlockHeaderFunctions();
  private final MergeMiningCoordinator mergeCoordinator;

  public EngineNewPayload(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator) {
    super(vertx, protocolContext);
    this.mergeCoordinator = mergeCoordinator;
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_NEW_PAYLOAD.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    final EnginePayloadParameter blockParam =
        requestContext.getRequiredParameter(0, EnginePayloadParameter.class);

    Object reqId = requestContext.getRequest().getId();

    traceLambda(LOG, "blockparam: {}", () -> Json.encodePrettily(blockParam));

    final List<Transaction> transactions;
    try {
      transactions =
          blockParam.getTransactions().stream()
              .map(Bytes::fromHexString)
              .map(TransactionDecoder::decodeOpaqueBytes)
              .collect(Collectors.toList());
    } catch (final RLPException | IllegalArgumentException e) {
      return respondWithInvalid(
          reqId,
          blockParam,
          mergeCoordinator.getLatestValidAncestor(blockParam.getParentHash()).orElse(null),
          "Failed to decode transactions from block parameter");
    }

    if (blockParam.getExtraData() == null) {
      return respondWithInvalid(
          reqId,
          blockParam,
          mergeCoordinator.getLatestValidAncestor(blockParam.getParentHash()).orElse(null),
          "Field extraData must not be null");
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
            blockParam.getPrevRandao(),
            0,
            headerFunctions);

    // ensure the block hash matches the blockParam hash
    // this must be done before any other check
    if (!newBlockHeader.getHash().equals(blockParam.getBlockHash())) {
      LOG.debug(
          String.format(
              "Computed block hash %s does not match block hash parameter %s",
              newBlockHeader.getBlockHash(), blockParam.getBlockHash()));
      return respondWith(reqId, blockParam, null, INVALID_BLOCK_HASH);
    }
    // do we already have this payload
    if (protocolContext.getBlockchain().getBlockByHash(newBlockHeader.getBlockHash()).isPresent()) {
      LOG.debug("block already present");
      return respondWith(reqId, blockParam, blockParam.getBlockHash(), VALID);
    }
    if (mergeCoordinator.isBadBlock(blockParam.getBlockHash())) {
      return respondWith(
          reqId,
          blockParam,
          mergeCoordinator
              .getLatestValidHashOfBadBlock(blockParam.getBlockHash())
              .orElse(Hash.ZERO),
          INVALID);
    }

    Optional<BlockHeader> parentHeader =
        protocolContext.getBlockchain().getBlockHeader(blockParam.getParentHash());
    if (parentHeader.isPresent()
        && (blockParam.getTimestamp() <= parentHeader.get().getTimestamp())) {
      return respondWithInvalid(
          reqId,
          blockParam,
          mergeCoordinator.getLatestValidAncestor(blockParam.getParentHash()).orElse(null),
          "block timestamp not greater than parent");
    }

    final var block =
        new Block(newBlockHeader, new BlockBody(transactions, Collections.emptyList()));

    if (mergeContext.get().isSyncing() || parentHeader.isEmpty()) {
      LOG.debug(
          "isSyncing: {} parentHeaderMissing: {}, adding {} to backwardsync",
          mergeContext.get().isSyncing(),
          parentHeader.isEmpty(),
          block.getHash());
      mergeCoordinator
          .appendNewPayloadToSync(block)
          .exceptionally(
              exception -> {
                LOG.warn(
                    "Sync to block " + block.toLogString() + " failed", exception.getMessage());
                return null;
              });
      return respondWith(reqId, blockParam, null, SYNCING);
    }

    // TODO: post-merge cleanup
    if (!mergeCoordinator.latestValidAncestorDescendsFromTerminal(newBlockHeader)) {
      mergeCoordinator.addBadBlock(block);
      return respondWithInvalid(
          reqId,
          blockParam,
          Hash.ZERO,
          newBlockHeader.getHash() + " did not descend from terminal block");
    }

    final var latestValidAncestor = mergeCoordinator.getLatestValidAncestor(newBlockHeader);

    if (latestValidAncestor.isEmpty()) {
      return respondWith(reqId, blockParam, null, ACCEPTED);
    }

    // execute block and return result response
    final BlockValidator.Result executionResult = mergeCoordinator.rememberBlock(block);

    if (executionResult.errorMessage.isEmpty()) {
      return respondWith(reqId, blockParam, newBlockHeader.getHash(), VALID);
    } else {
      LOG.debug("New payload is invalid: {}", executionResult.errorMessage.get());
      return respondWithInvalid(
          reqId, blockParam, latestValidAncestor.get(), executionResult.errorMessage.get());
    }
  }

  JsonRpcResponse respondWith(
      final Object requestId,
      final EnginePayloadParameter param,
      final Hash latestValidHash,
      final EngineStatus status) {
    debugLambda(
        LOG,
        "New payload: number: {}, hash: {}, parentHash: {}, latestValidHash: {}, status: {}",
        () -> param.getBlockNumber(),
        () -> param.getBlockHash(),
        () -> param.getParentHash(),
        () -> latestValidHash == null ? null : latestValidHash.toHexString(),
        status::name);
    return new JsonRpcSuccessResponse(
        requestId, new EnginePayloadStatusResult(status, latestValidHash, Optional.empty()));
  }

  // engine api calls are synchronous, no need for volatile
  private long lastInvalidWarn = System.currentTimeMillis();

  JsonRpcResponse respondWithInvalid(
      final Object requestId,
      final EnginePayloadParameter param,
      final Hash latestValidHash,
      final String validationError) {
    if (lastInvalidWarn + ENGINE_API_LOGGING_THRESHOLD < System.currentTimeMillis()) {
      lastInvalidWarn = System.currentTimeMillis();
      warnLambda(
          LOG,
          "Invalid new payload: number: {}, hash: {}, parentHash: {}, latestValidHash: {}, status: {}, validationError: {}",
          () -> param.getBlockNumber(),
          () -> param.getBlockHash(),
          () -> param.getParentHash(),
          () -> latestValidHash == null ? null : latestValidHash.toHexString(),
          INVALID::name,
          () -> validationError);
    }
    return new JsonRpcSuccessResponse(
        requestId,
        new EnginePayloadStatusResult(INVALID, latestValidHash, Optional.of(validationError)));
  }
}
