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

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
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

/** The type Abstract engine get payload. */
public abstract class AbstractEngineGetPayload extends ExecutionEngineJsonRpcMethod {

  private final MergeMiningCoordinator mergeMiningCoordinator;

  /** The Block result factory. */
  protected final BlockResultFactory blockResultFactory;

  private static final Logger LOG = LoggerFactory.getLogger(AbstractEngineGetPayload.class);

  /**
   * Instantiates a new Abstract engine get payload.
   *
   * @param vertx the vertx
   * @param schedule the schedule
   * @param protocolContext the protocol context
   * @param mergeMiningCoordinator the merge mining coordinator
   * @param blockResultFactory the block result factory
   * @param engineCallListener the engine call listener
   */
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

  /**
   * Instantiates a new Abstract engine get payload.
   *
   * @param vertx the vertx
   * @param protocolContext the protocol context
   * @param mergeMiningCoordinator the merge mining coordinator
   * @param blockResultFactory the block result factory
   * @param engineCallListener the engine call listener
   */
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

    final PayloadIdentifier payloadId = request.getRequiredParameter(0, PayloadIdentifier.class);
    mergeMiningCoordinator.finalizeProposalById(payloadId);
    final Optional<BlockWithReceipts> blockWithReceipts =
        mergeContext.get().retrieveBlockById(payloadId);
    if (blockWithReceipts.isPresent()) {
      final BlockWithReceipts proposal = blockWithReceipts.get();
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
      return createResponse(request, payloadId, proposal);
    }
    return new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.UNKNOWN_PAYLOAD);
  }

  /**
   * Log proposal.
   *
   * @param payloadId the payload id
   * @param proposal the proposal
   * @param maybeReward the maybe reward
   */
  protected void logProposal(
      final PayloadIdentifier payloadId,
      final BlockWithReceipts proposal,
      final Optional<Wei> maybeReward) {
    final BlockHeader proposalHeader = proposal.getHeader();
    final float gasUsedPerc = 100.0f * proposalHeader.getGasUsed() / proposalHeader.getGasLimit();

    final String message =
        "Fetch block proposal by identifier: {}, hash: {}, "
            + "number: {}, coinbase: {}, transaction count: {}, gas used: {}%"
            + maybeReward.map(unused -> ", reward: {}").orElse("{}");

    LOG.atInfo()
        .setMessage(message)
        .addArgument(payloadId::toHexString)
        .addArgument(proposalHeader::getHash)
        .addArgument(proposalHeader::getNumber)
        .addArgument(proposalHeader::getCoinbase)
        .addArgument(() -> proposal.getBlock().getBody().getTransactions().size())
        .addArgument(() -> String.format("%1.2f", gasUsedPerc))
        .addArgument(maybeReward.map(Wei::toHumanReadableString).orElse(""))
        .log();
  }

  /**
   * Create response json rpc response.
   *
   * @param request the request
   * @param payloadId the payload id
   * @param blockWithReceipts the block with receipts
   * @return the json rpc response
   */
  protected abstract JsonRpcResponse createResponse(
      final JsonRpcRequestContext request,
      final PayloadIdentifier payloadId,
      final BlockWithReceipts blockWithReceipts);
}
