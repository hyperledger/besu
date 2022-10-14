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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.EngineExchangeTransitionConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.EngineForkchoiceUpdated;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.EngineGetPayload;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.EngineNewPayload;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.EngineQosTimer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;

import java.util.Map;
import java.util.Optional;

import io.vertx.core.Vertx;

public class ExecutionEngineJsonRpcMethods extends ApiGroupJsonRpcMethods {

  private final BlockResultFactory blockResultFactory = new BlockResultFactory();

  private final Optional<MergeMiningCoordinator> mergeCoordinator;
  private final ProtocolContext protocolContext;
  private final EthPeers ethPeers;
  private final Vertx consensusEngineServer;

  ExecutionEngineJsonRpcMethods(
      final MiningCoordinator miningCoordinator,
      final ProtocolContext protocolContext,
      final EthPeers ethPeers,
      final Vertx consensusEngineServer) {
    this.mergeCoordinator =
        Optional.ofNullable(miningCoordinator)
            .filter(mc -> mc.isCompatibleWithEngineApi())
            .map(MergeMiningCoordinator.class::cast);

    this.protocolContext = protocolContext;
    this.ethPeers = ethPeers;
    this.consensusEngineServer = consensusEngineServer;
  }

  @Override
  protected String getApiGroup() {
    return RpcApis.ENGINE.name();
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    final EngineQosTimer engineQosTimer = new EngineQosTimer(consensusEngineServer);

    if (mergeCoordinator.isPresent()) {
      return mapOf(
          new EngineGetPayload(
              consensusEngineServer,
              protocolContext,
              mergeCoordinator.get(),
              blockResultFactory,
              engineQosTimer),
          new EngineNewPayload(
              consensusEngineServer,
              protocolContext,
              mergeCoordinator.get(),
              ethPeers,
              engineQosTimer),
          new EngineForkchoiceUpdated(
              consensusEngineServer, protocolContext, mergeCoordinator.get(), engineQosTimer),
          new EngineExchangeTransitionConfiguration(
              consensusEngineServer, protocolContext, engineQosTimer));
    } else {
      return mapOf(
          new EngineExchangeTransitionConfiguration(
              consensusEngineServer, protocolContext, engineQosTimer));
    }
  }
}
