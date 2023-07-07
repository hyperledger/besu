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

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INVALID_PARAMS;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.UNSUPPORTED_FORK;

public class EngineNewPayloadV6110 extends EngineNewPayloadV3 {

  private final ProtocolSchedule timestampSchedule;

  public EngineNewPayloadV6110(
      final Vertx vertx,
      final ProtocolSchedule timestampSchedule,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator,
      final EthPeers ethPeers,
      final EngineCallListener engineCallListener) {
    super(
        vertx, timestampSchedule, protocolContext, mergeCoordinator, ethPeers, engineCallListener);
    this.timestampSchedule = timestampSchedule;
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_NEW_PAYLOAD_V6110.getMethodName();
  }

  @Override
  protected Optional<JsonRpcResponse> validateForkSupported(
      final Object reqId, final EnginePayloadParameter payloadParameter) {
    var eip6110 = timestampSchedule.hardforkFor(s -> s.fork().name().equalsIgnoreCase("ExperimentalEips"));

    // TODO-6110: Need to double check the condition on returning UNSUPPORTED_FORK
    if (eip6110.isPresent() && payloadParameter.getTimestamp() >= eip6110.get().milestone()) {
      if (payloadParameter.getDataGasUsed() == null
          || payloadParameter.getExcessDataGas() == null) {
        return Optional.of(new JsonRpcErrorResponse(reqId, INVALID_PARAMS));
      } else {
        return Optional.empty();
      }
    } else {
        return Optional.of(new JsonRpcErrorResponse(reqId, UNSUPPORTED_FORK));
    }
  }
}
