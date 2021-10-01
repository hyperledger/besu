/*
 * Copyright ConsenSys AG.
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

import org.hyperledger.besu.consensus.merge.blockcreation.MergeCoordinator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.ExecutionPreparePayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import io.vertx.core.Vertx;

public class EnginePreparePayload extends ExecutionEngineJsonRpcMethod {
  private final MergeCoordinator mergeCoordinator;

  public EnginePreparePayload(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final MergeCoordinator mergeCoordinator) {
    super(vertx, protocolContext);
    this.mergeCoordinator = mergeCoordinator;
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_PREPARE_PAYLOAD.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    final ExecutionPreparePayloadParameter executionPreparePayloadParameter =
        requestContext.getRequiredParameter(0, ExecutionPreparePayloadParameter.class);

    // todo respond with error if we're syncing

    return protocolContext
        .getBlockchain()
        .getBlockHeader(executionPreparePayloadParameter.getParentHash())
        .<JsonRpcResponse>map(
            parentHeader ->
                new JsonRpcSuccessResponse(
                    requestContext.getRequest().getId(),
                    mergeCoordinator.preparePayload(
                        parentHeader,
                        executionPreparePayloadParameter.getTimestamp(),
                        executionPreparePayloadParameter.getRandom(),
                        executionPreparePayloadParameter.getFeeRecipient())))
        .orElse(
            new JsonRpcErrorResponse(
                requestContext.getRequest().getId(), JsonRpcError.UNKNOWN_HEADER));
  }
}
