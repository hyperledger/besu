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
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.mainnet.DefaultTimestampSchedule;
import org.hyperledger.besu.ethereum.mainnet.TimestampSchedule;

import io.vertx.core.Vertx;

public class EngineGetPayloadV3 extends AbstractEngineGetPayload {

  public EngineGetPayloadV3(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeMiningCoordinator,
      final BlockResultFactory blockResultFactory,
      final EngineCallListener engineCallListener,
      final TimestampSchedule schedule) {
    super(
        vertx,
        protocolContext,
        mergeMiningCoordinator,
        blockResultFactory,
        engineCallListener,
        schedule);
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_GET_PAYLOAD_V3.getMethodName();
  }

  @Override
  protected JsonRpcResponse createResponse(
      final JsonRpcRequestContext request, final BlockWithReceipts blockWithReceipts) {

    DefaultTimestampSchedule tsched = (DefaultTimestampSchedule) this.schedule.get();
    long shanghaiTimestamp = tsched.scheduledAt("Shanghai");
    long cancunTimestamp = tsched.scheduledAt("Cancun");
    long builtAt = blockWithReceipts.getHeader().getTimestamp();
    if (builtAt < shanghaiTimestamp) {
      return new JsonRpcSuccessResponse(
          request.getRequest().getId(),
          blockResultFactory.payloadTransactionCompleteV1(blockWithReceipts.getBlock()));
    } else if (builtAt >= shanghaiTimestamp && builtAt < cancunTimestamp) {
      return new JsonRpcSuccessResponse(
          request.getRequest().getId(),
          blockResultFactory.payloadTransactionCompleteV2(blockWithReceipts));
    } else if (builtAt >= cancunTimestamp) {
      return new JsonRpcSuccessResponse(
          request.getRequest().getId(),
          blockResultFactory.payloadTransactionCompleteV3(blockWithReceipts));
    }

    return new JsonRpcSuccessResponse(
        request.getRequest().getId(),
        blockResultFactory.payloadTransactionCompleteV3(blockWithReceipts));
  }
}
