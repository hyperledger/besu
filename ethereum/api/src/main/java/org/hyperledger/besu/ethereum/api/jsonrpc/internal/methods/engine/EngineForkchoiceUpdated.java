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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.SYNCING;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.VALID;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator.ForkchoiceResult;
import org.hyperledger.besu.consensus.merge.blockcreation.PayloadAttributes;
import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EngineForkchoiceUpdatedParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadAttributesParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
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
    final Object requestId = requestContext.getRequest().getId();

    final EngineForkchoiceUpdatedParameter forkChoice =
        requestContext.getRequiredParameter(0, EngineForkchoiceUpdatedParameter.class);
    final Optional<EnginePayloadAttributesParameter> maybePayloadAttributes =
        requestContext.getOptionalParameter(1, EnginePayloadAttributesParameter.class);

    Optional<Hash> maybeFinalizedHash =
        Optional.ofNullable(forkChoice.getFinalizedBlockHash())
            .filter(finalized -> !finalized.isZero());

    mergeContext
        .get()
        .fireNewUnverifiedForkchoiceMessageEvent(
            forkChoice.getHeadBlockHash(), maybeFinalizedHash, forkChoice.getSafeBlockHash());

    if (mergeContext.get().isSyncing()) {
      return syncingResponse(requestId, forkChoice);
    }

    if (mergeCoordinator.isBadBlock(forkChoice.getHeadBlockHash())) {
      logForkchoiceUpdatedCall(INVALID, forkChoice);
      return new JsonRpcSuccessResponse(
          requestId,
          new EngineUpdateForkchoiceResult(
              INVALID,
              mergeCoordinator
                  .getLatestValidHashOfBadBlock(forkChoice.getHeadBlockHash())
                  .orElse(Hash.ZERO),
              null,
              Optional.of(forkChoice.getHeadBlockHash() + " is an invalid block")));
    }

    Optional<BlockHeader> newHead =
        mergeCoordinator.getOrSyncHeaderByHash(forkChoice.getHeadBlockHash());

    if (newHead.isEmpty()) {
      return syncingResponse(requestId, forkChoice);
    }

    maybePayloadAttributes.ifPresentOrElse(
        this::logPayload, () -> LOG.debug("Payload attributes are null"));

    if (!isValidForkchoiceState(
        forkChoice.getSafeBlockHash(), forkChoice.getFinalizedBlockHash(), newHead.get())) {
      logForkchoiceUpdatedCall(INVALID, forkChoice);
      return new JsonRpcErrorResponse(requestId, JsonRpcError.INVALID_FORKCHOICE_STATE);
    }

    // TODO: post-merge cleanup, this should be unnecessary after merge
    if (!mergeCoordinator.latestValidAncestorDescendsFromTerminal(newHead.get())) {
      logForkchoiceUpdatedCall(INVALID, forkChoice);
      return new JsonRpcSuccessResponse(
          requestId,
          new EngineUpdateForkchoiceResult(
              INVALID,
              Hash.ZERO,
              null,
              Optional.of(newHead.get() + " did not descend from terminal block")));
    }

    ForkchoiceResult result =
        mergeCoordinator.updateForkChoice(
            newHead.get(),
            forkChoice.getFinalizedBlockHash(),
            forkChoice.getSafeBlockHash(),
            maybePayloadAttributes.map(
                payloadAttributes ->
                    new PayloadAttributes(
                        payloadAttributes.getTimestamp(),
                        payloadAttributes.getPrevRandao(),
                        payloadAttributes.getSuggestedFeeRecipient())));

    if (!result.isValid()) {
      logForkchoiceUpdatedCall(INVALID, forkChoice);
      return handleNonValidForkchoiceUpdate(requestId, result);
    }

    // begin preparing a block if we have a non-empty payload attributes param
    Optional<PayloadIdentifier> payloadId =
        maybePayloadAttributes.map(
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
                () -> maybePayloadAttributes.map(EnginePayloadAttributesParameter::serialize)));

    logForkchoiceUpdatedCall(VALID, forkChoice);
    return new JsonRpcSuccessResponse(
        requestId,
        new EngineUpdateForkchoiceResult(
            VALID,
            result.getNewHead().map(BlockHeader::getHash).orElse(null),
            payloadId.orElse(null),
            Optional.empty()));
  }

  private JsonRpcResponse handleNonValidForkchoiceUpdate(
      final Object requestId, final ForkchoiceResult result) {
    JsonRpcResponse response;

    final Optional<Hash> latestValid = result.getLatestValid();

    switch (result.getStatus()) {
      case INVALID:
        response =
            new JsonRpcSuccessResponse(
                requestId,
                new EngineUpdateForkchoiceResult(
                    INVALID, latestValid.orElse(null), null, result.getErrorMessage()));
        break;
      case INVALID_PAYLOAD_ATTRIBUTES:
        response = new JsonRpcErrorResponse(requestId, JsonRpcError.INVALID_PAYLOAD_ATTRIBUTES);
        break;
      case IGNORE_UPDATE_TO_OLD_HEAD:
        response =
            new JsonRpcSuccessResponse(
                requestId,
                new EngineUpdateForkchoiceResult(
                    VALID, latestValid.orElse(null), null, result.getErrorMessage()));
        break;
      default:
        throw new AssertionError(
            "ForkchoiceResult.Status "
                + result.getStatus()
                + " not handled in EngineForkchoiceUpdated.handleForkchoiceError");
    }

    return response;
  }

  private void logPayload(final EnginePayloadAttributesParameter payloadAttributes) {
    debugLambda(
        LOG,
        "timestamp: {}, prevRandao: {}, suggestedFeeRecipient: {}",
        payloadAttributes::getTimestamp,
        () -> payloadAttributes.getPrevRandao().toHexString(),
        () -> payloadAttributes.getSuggestedFeeRecipient().toHexString());
  }

  private boolean isValidForkchoiceState(
      final Hash safeBlockHash, final Hash finalizedBlockHash, final BlockHeader newBlock) {
    Optional<BlockHeader> maybeFinalizedBlock = Optional.empty();

    if (!finalizedBlockHash.isZero()) {
      maybeFinalizedBlock = protocolContext.getBlockchain().getBlockHeader(finalizedBlockHash);

      // if the finalized block hash is not zero, we always need to have its block, because we
      // only do this check once we have finished syncing
      if (maybeFinalizedBlock.isEmpty()) {
        return false;
      }

      // a valid finalized block must be an ancestor of the new head
      if (!mergeCoordinator.isDescendantOf(maybeFinalizedBlock.get(), newBlock)) {
        return false;
      }
    }

    // A zero value is only allowed, if the transition block is not yet finalized.
    // Once we have at least one finalized block, the transition block has either been finalized
    // directly
    // or through one of its descendants.
    if (safeBlockHash.isZero()) {
      return finalizedBlockHash.isZero();
    }

    final Optional<BlockHeader> maybeSafeBlock =
        protocolContext.getBlockchain().getBlockHeader(safeBlockHash);

    // if the safe block hash is not zero, we always need to have its block, because we
    // only do this check once we have finished syncing
    if (maybeSafeBlock.isEmpty()) {
      return false;
    }

    // a valid safe block must be a descendant of the finalized block
    if (maybeFinalizedBlock.isPresent()
        && !mergeCoordinator.isDescendantOf(maybeFinalizedBlock.get(), maybeSafeBlock.get())) {
      return false;
    }

    // a valid safe block must be an ancestor of the new block
    return mergeCoordinator.isDescendantOf(maybeSafeBlock.get(), newBlock);
  }

  private JsonRpcResponse syncingResponse(
      final Object requestId, final EngineForkchoiceUpdatedParameter forkChoice) {

    logForkchoiceUpdatedCall(SYNCING, forkChoice);
    return new JsonRpcSuccessResponse(
        requestId, new EngineUpdateForkchoiceResult(SYNCING, null, null, Optional.empty()));
  }

  // fcU calls are synchronous, no need to make volatile
  private long lastFcuInfoLog = System.currentTimeMillis();

  private void logForkchoiceUpdatedCall(
      final EngineStatus status, final EngineForkchoiceUpdatedParameter forkChoice) {
    // cheaply limit the noise of fcU during consensus client syncing to once a minute:
    if (lastFcuInfoLog + ENGINE_API_LOGGING_THRESHOLD < System.currentTimeMillis()) {
      lastFcuInfoLog = System.currentTimeMillis();
      LOG.info(
          "{} for fork-choice-update: head: {}, finalized: {}, safeBlockHash: {}",
          status.name(),
          forkChoice.getHeadBlockHash(),
          forkChoice.getFinalizedBlockHash(),
          forkChoice.getSafeBlockHash());
    }
  }
}
