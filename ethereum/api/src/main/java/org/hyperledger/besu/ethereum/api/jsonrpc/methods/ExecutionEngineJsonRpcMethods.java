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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.EngineExecutePayload;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.EngineForkchoiceUpdated;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.EngineGetPayload;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;

import java.util.Map;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class ExecutionEngineJsonRpcMethods extends ApiGroupJsonRpcMethods {

  private final BlockResultFactory blockResultFactory = new BlockResultFactory();

  private final MergeMiningCoordinator mergeCoordinator;
  private final ProtocolContext protocolContext;

  ExecutionEngineJsonRpcMethods(
      final MiningCoordinator miningCoordinator, final ProtocolContext protocolContext) {
    this.mergeCoordinator = (MergeMiningCoordinator) miningCoordinator;
    this.protocolContext = protocolContext;
  }

  @Override
  protected String getApiGroup() {
    return RpcApis.EXECUTION.name();
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    Vertx syncVertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(1));
    return mapOf(
        new EngineGetPayload(syncVertx, protocolContext, blockResultFactory),
        new EngineExecutePayload(syncVertx, protocolContext, mergeCoordinator),
        new EngineForkchoiceUpdated(syncVertx, protocolContext, mergeCoordinator));
  }
}
