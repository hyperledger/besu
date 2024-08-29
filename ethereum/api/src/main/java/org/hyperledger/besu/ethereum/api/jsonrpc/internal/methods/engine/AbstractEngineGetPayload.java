/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.consensus.merge.PayloadWrapper;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.Optional;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractEngineGetPayload extends ExecutionEngineJsonRpcMethod {

  private final MergeMiningCoordinator mergeMiningCoordinator;
  protected final BlockResultFactory blockResultFactory;
  private static final Logger LOG = LoggerFactory.getLogger(AbstractEngineGetPayload.class);

  public AbstractEngineGetPayload(
      final Vertx vertx,
      final ProtocolSchedule schedule,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeMiningCoordinator,
      final BlockResultFactory blockResultFactory,
      final EngineCallListener engineCallListener) {
    super(vertx, schedule, protocolContext, engineCallListener);
    this.mergeMiningCoordinator = mergeMiningCoordinator;
    this.blockResultFactory = blockResultFactory;
  }

  public AbstractEngineGetPayload(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeMiningCoordinator,
      final BlockResultFactory blockResultFactory,
      final EngineCallListener engineCallListener) {
    super(vertx, protocolContext, engineCallListener);
    this.mergeMiningCoordinator = mergeMiningCoordinator;
    this.blockResultFactory = blockResultFactory;
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext request) {
    engineCallListener.executionEngineCalled();

    final PayloadIdentifier payloadId;
    try {
      payloadId = request.getRequiredParameter(0, PayloadIdentifier.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid payload ID parameter (index 0)", RpcErrorType.INVALID_PAYLOAD_ID_PARAMS, e);
    }
    mergeMiningCoordinator.finalizeProposalById(payloadId);
    final Optional<PayloadWrapper> maybePayload = mergeContext.get().retrievePayloadById(payloadId);
    if (maybePayload.isPresent()) {
      final BlockWithReceipts proposal = maybePayload.get().blockWithReceipts();
      LOG.atDebug()
          .setMessage("assembledBlock for payloadId {}: {}")
          .addArgument(() -> payloadId)
          .addArgument(() -> proposal.getBlock().toLogString())
          .log();
      LOG.atTrace().setMessage("assembledBlock with receipts {}").addArgument(() -> proposal).log();
      ValidationResult<RpcErrorType> forkValidationResult =
          validateForkSupported(proposal.getHeader().getTimestamp());
      if (!forkValidationResult.isValid()) {
        return new JsonRpcErrorResponse(request.getRequest().getId(), forkValidationResult);
      }
      return createResponse(request, maybePayload.get());
    }
    return new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.UNKNOWN_PAYLOAD);
  }

  protected void logProposal(final PayloadWrapper payload) {
    final BlockHeader proposalHeader = payload.blockWithReceipts().getHeader();
    final float gasUsedPerc = 100.0f * proposalHeader.getGasUsed() / proposalHeader.getGasLimit();

    final String message =
        "Fetch block proposal by identifier: {}, hash: {}, "
            + "number: {}, coinbase: {}, transaction count: {}, gas used: {}%"
            + " reward: {}";

    LOG.atInfo()
        .setMessage(message)
        .addArgument(payload.payloadIdentifier()::toHexString)
        .addArgument(proposalHeader::getHash)
        .addArgument(proposalHeader::getNumber)
        .addArgument(proposalHeader::getCoinbase)
        .addArgument(
            () -> payload.blockWithReceipts().getBlock().getBody().getTransactions().size())
        .addArgument(() -> String.format("%1.2f", gasUsedPerc))
        .addArgument(payload.blockValue()::toHumanReadableString)
        .log();
  }

  protected abstract JsonRpcResponse createResponse(
      final JsonRpcRequestContext request, final PayloadWrapper payload);
}
