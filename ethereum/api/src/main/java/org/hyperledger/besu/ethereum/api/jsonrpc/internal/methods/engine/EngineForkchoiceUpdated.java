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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EngineForkChoiceUpdatedParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadAttributesParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineUpdateForkChoiceResult;
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

    final EngineForkChoiceUpdatedParameter forkChoice =
        requestContext.getRequiredParameter(0, EngineForkChoiceUpdatedParameter.class);
    final Optional<EnginePayloadAttributesParameter> optionalPayloadAttributes =
        requestContext.getOptionalParameter(1, EnginePayloadAttributesParameter.class);

    if (mergeContext.isSyncing() || mergeCoordinator.isBackwardSyncing()) {
      // if we are syncing, return SYNCING
      return new JsonRpcSuccessResponse(
          requestContext.getRequest().getId(),
          new EngineUpdateForkChoiceResult(ForkChoiceStatus.SYNCING, null));
    }

    LOG.info(
        "Consensus fork-choice-update: head: {}, finalized: {}",
        forkChoice.getHeadBlockHash(),
        forkChoice.getFinalizedBlockHash());

    Optional<BlockHeader> currentHead =
        protocolContext.getBlockchain().getBlockHeader(forkChoice.getHeadBlockHash());

    if (currentHead.isPresent()) {

      // TODO: post-merge cleanup
      if (!mergeCoordinator.latestValidAncestorDescendsFromTerminal(currentHead.get())) {
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), JsonRpcError.INVALID_TERMINAL_BLOCK);
      }

      // update fork choice
      mergeCoordinator.updateForkChoice(
          forkChoice.getHeadBlockHash(), forkChoice.getFinalizedBlockHash());

      // begin preparing a block if we have a non-empty payload attributes param
      Optional<PayloadIdentifier> payloadId =
          optionalPayloadAttributes.map(
              payloadAttributes ->
                  mergeCoordinator.preparePayload(
                      currentHead.get(),
                      payloadAttributes.getTimestamp(),
                      payloadAttributes.getRandom(),
                      payloadAttributes.getSuggestedFeeRecipient()));

      payloadId.ifPresent(
          pid ->
              debugLambda(
                  LOG,
                  "returning identifier {} for requested payload {}",
                  pid::toHexString,
                  () ->
                      optionalPayloadAttributes.map(EnginePayloadAttributesParameter::serialize)));

      return new JsonRpcSuccessResponse(
          requestContext.getRequest().getId(),
          new EngineUpdateForkChoiceResult(ForkChoiceStatus.SUCCESS, payloadId.orElse(null)));
    }

    // else fail with parent not found
    return new JsonRpcErrorResponse(
        requestContext.getRequest().getId(), JsonRpcError.PARENT_BLOCK_NOT_FOUND);
  }
}
