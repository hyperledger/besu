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

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;

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
      final var proposal = blockWithReceipts.get();
      final var proposalHeader = proposal.getHeader();
      LOG.atInfo()
          .setMessage(
              "Fetch block proposal by identifier: {}, hash: {}, number: {}, coinbase: {}, transaction count: {}")
          .addArgument(payloadId::toHexString)
          .addArgument(proposalHeader::getHash)
          .addArgument(proposalHeader::getNumber)
          .addArgument(proposalHeader::getCoinbase)
          .addArgument(() -> proposal.getBlock().getBody().getTransactions().size())
          .log();
      LOG.atDebug().setMessage("assembledBlock {}").addArgument(() -> proposal).log();
      return createResponse(request, proposal);
    }
    return new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.UNKNOWN_PAYLOAD);
  }

  protected abstract JsonRpcResponse createResponse(
      final JsonRpcRequestContext request, final BlockWithReceipts blockWithReceipts);
}
