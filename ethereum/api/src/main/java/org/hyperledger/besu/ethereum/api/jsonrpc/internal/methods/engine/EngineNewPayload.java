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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID_TERMINAL_BLOCK;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.SYNCING;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.VALID;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

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
          mergeCoordinator.getLatestValidAncestor(blockParam.getParentHash()).orElse(null),
          "Failed to decode transactions from block parameter");
    }

    if (blockParam.getExtraData() == null) {
      return respondWithInvalid(
          reqId,
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
      return respondWith(reqId, null, INVALID_BLOCK_HASH);
    } else {
      // do we already have this payload
      if (protocolContext
          .getBlockchain()
          .getBlockByHash(newBlockHeader.getBlockHash())
          .isPresent()) {
        LOG.debug("block already present");
        return respondWith(reqId, blockParam.getBlockHash(), VALID);
      }

      Optional<BlockHeader> parentHeader =
          protocolContext.getBlockchain().getBlockHeader(blockParam.getParentHash());
      if (parentHeader.isPresent()) {
        if (!(blockParam.getTimestamp() > parentHeader.get().getTimestamp())) {
          return respondWithInvalid(
              reqId, parentHeader.get().getHash(), "Timestamp must be greater than parent");
        }
      } else {

      }
    }

    final var block =
        new Block(newBlockHeader, new BlockBody(transactions, Collections.emptyList()));

    if (mergeContext.isSyncing()
        || mergeCoordinator.getOrSyncHeaderByHash(newBlockHeader.getParentHash()).isEmpty()) {
      mergeCoordinator.appendNewPayloadToSync(block);
      return respondWith(reqId, null, SYNCING);
    }

    // TODO: post-merge cleanup
    if (!mergeCoordinator.latestValidAncestorDescendsFromTerminal(newBlockHeader)) {
      return respondWith(requestContext.getRequest().getId(), null, INVALID_TERMINAL_BLOCK);
    }

    final var latestValidAncestor = mergeCoordinator.getLatestValidAncestor(newBlockHeader);

    if (latestValidAncestor.isEmpty()) {
      return respondWith(reqId, null, ACCEPTED);
    }

    // execute block and return result response
    final BlockValidator.Result executionResult = mergeCoordinator.executeBlock(block);

    if (executionResult.errorMessage.isEmpty()) {
      return respondWith(reqId, newBlockHeader.getHash(), VALID);
    } else {
      LOG.debug("New payload is invalid: {}", executionResult.errorMessage.get());
      return respondWithInvalid(
          reqId, latestValidAncestor.get(), executionResult.errorMessage.get());
    }
  }

  JsonRpcResponse respondWith(
      final Object requestId, final Hash latestValidHash, final EngineStatus status) {
    traceLambda(
        LOG,
        "Response: requestId: {}, latestValidHash: {}, status: {}",
        () -> requestId,
        () -> latestValidHash == null ? null : latestValidHash.toHexString(),
        status::name);
    return new JsonRpcSuccessResponse(
        requestId, new EnginePayloadStatusResult(status, latestValidHash, null));
  }

  JsonRpcResponse respondWithInvalid(
      final Object requestId, final Hash latestValidHash, final String validationError) {
    traceLambda(
        LOG,
        "Response: requestId: {}, latestValidHash: {}, status: {}, validationError: {}",
        () -> requestId,
        () -> latestValidHash == null ? null : latestValidHash.toHexString(),
        INVALID::name,
        () -> validationError);
    return new JsonRpcSuccessResponse(
        requestId, new EnginePayloadStatusResult(INVALID, latestValidHash, validationError));
  }
}
