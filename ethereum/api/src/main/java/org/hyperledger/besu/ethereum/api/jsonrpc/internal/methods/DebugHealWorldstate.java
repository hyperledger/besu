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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class DebugHealWorldstate implements JsonRpcMethod {
  private final Synchronizer synchronizer;
  private final ProtocolSchedule protocolSchedule;
  private final Blockchain blockchain;

  public DebugHealWorldstate(
      final ProtocolSchedule protocolSchedule,
      final Blockchain blockchain,
      final Synchronizer synchronizer) {
    this.synchronizer = synchronizer;
    this.protocolSchedule = protocolSchedule;
    this.blockchain = blockchain;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_HEAL_WORLDSTATE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext request) {
    protocolSchedule
        .getByBlockHeader(blockchain.getChainHeadHeader())
        .getBadBlocksManager()
        .reset();
    return new JsonRpcSuccessResponse(
        request.getRequest().getId(),
        synchronizer.healWorldState(Optional.empty(), Bytes.EMPTY, false));
  }
}
