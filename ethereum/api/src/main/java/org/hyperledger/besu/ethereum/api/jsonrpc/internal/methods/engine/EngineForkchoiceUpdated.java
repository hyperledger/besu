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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID_TERMINAL_BLOCK;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.SYNCING;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.VALID;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator.ForkchoiceResult;
import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EngineForkchoiceUpdatedParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadAttributesParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineUpdateForkchoiceResult;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Optional;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineForkchoiceUpdated extends ExecutionEngineJsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(EngineForkchoiceUpdated.class);
  private final MergeMiningCoordinator mergeCoordinator;

  public EngineForkchoiceUpdated(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator) {
    super(vertx, protocolContext);
    this.mergeCoordinator = mergeCoordinator;
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_FORKCHOICE_UPDATED.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {

    final EngineForkchoiceUpdatedParameter forkChoice =
        requestContext.getRequiredParameter(0, EngineForkchoiceUpdatedParameter.class);
    final Optional<EnginePayloadAttributesParameter> optionalPayloadAttributes =
        requestContext.getOptionalParameter(1, EnginePayloadAttributesParameter.class);

    if (mergeContext.isSyncing()) {
      return new JsonRpcSuccessResponse(
          requestContext.getRequest().getId(),
          new EngineUpdateForkchoiceResult(SYNCING, null, null, Optional.empty()));
    }

    LOG.info(
        "Consensus fork-choice-update: head: {}, finalized: {}",
        forkChoice.getHeadBlockHash(),
        forkChoice.getFinalizedBlockHash());

    Optional<BlockHeader> newHead =
        protocolContext.getBlockchain().getBlockHeader(forkChoice.getHeadBlockHash());

    Optional<Hash> finalizedHash =
        Optional.ofNullable(forkChoice.getFinalizedBlockHash())
            .filter(finalized -> !Hash.ZERO.equals(finalized));

    Optional<BlockHeader> finalizedHead =
        finalizedHash.flatMap(protocolContext.getBlockchain()::getBlockHeader);

    if (newHead.isPresent() && (finalizedHash.isEmpty() || finalizedHead.isPresent())) {

      // TODO: post-merge cleanup, this should be unnecessary after merge
      if (!mergeCoordinator.latestValidAncestorDescendsFromTerminal(newHead.get())) {
        return new JsonRpcSuccessResponse(
            requestContext.getRequest().getId(),
            new EngineUpdateForkchoiceResult(
                INVALID_TERMINAL_BLOCK,
                null,
                null,
                Optional.of(newHead.get() + " did not descend from terminal block")));
      }

      // update fork choice
      ForkchoiceResult result =
          mergeCoordinator.updateForkChoice(
              forkChoice.getHeadBlockHash(), forkChoice.getFinalizedBlockHash());

      // only build a block if forkchoice was successful
      if (result.isSuccessful()) {
        // begin preparing a block if we have a non-empty payload attributes param
        Optional<PayloadIdentifier> payloadId =
            optionalPayloadAttributes.map(
                payloadAttributes ->
                    mergeCoordinator.preparePayload(
                        newHead.get(),
                        payloadAttributes.getTimestamp(),
                        payloadAttributes.getPrevRandao(),
                        payloadAttributes.getSuggestedFeeRecipient()));

        payloadId.ifPresent(
            pid ->
                debugLambda(
                    LOG,
                    "returning identifier {} for requested payload {}",
                    pid::toHexString,
                    () ->
                        optionalPayloadAttributes.map(
                            EnginePayloadAttributesParameter::serialize)));

        return new JsonRpcSuccessResponse(
            requestContext.getRequest().getId(),
            new EngineUpdateForkchoiceResult(
                VALID,
                result.getNewHead().map(BlockHeader::getHash).orElse(null),
                payloadId.orElse(null),
                Optional.empty()));
      } else if (result.isFailed()) {
        final Optional<Hash> latestValid = result.getLatestValid();
        return new JsonRpcSuccessResponse(
            requestContext.getRequest().getId(),
            new EngineUpdateForkchoiceResult(
                INVALID,
                latestValid.isPresent() ? latestValid.get() : null,
                null,
                result.getErrorMessage()));
      }
    }

    Optional.ofNullable(forkChoice.getHeadBlockHash())
        .filter(hash -> !hash.equals(Hash.ZERO))
        .ifPresent(mergeCoordinator::getOrSyncHeaderByHash);

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        new EngineUpdateForkchoiceResult(SYNCING, null, null, Optional.empty()));
  }
}
