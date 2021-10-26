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

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.ExecutionForkChoiceUpdatedParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EngineForkchoiceUpdated extends ExecutionEngineJsonRpcMethod {
  private static final Logger LOG = LogManager.getLogger();

  public EngineForkchoiceUpdated(final Vertx vertx, final ProtocolContext protocolContext) {
    super(vertx, protocolContext);
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_FORKCHOICE_UPDATED.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    final ExecutionForkChoiceUpdatedParameter forkChoice =
        requestContext.getRequiredParameter(0, ExecutionForkChoiceUpdatedParameter.class);
    LOG.info(
        "Consensus fork-choice-update: head: {}, finalized: {}",
        forkChoice.getHeadBlockHash(),
        forkChoice.getFinalizedBlockHash());

    mergeContext.updateForkChoice(
        forkChoice.getHeadBlockHash(), forkChoice.getFinalizedBlockHash());

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
  }
}
