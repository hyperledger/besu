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
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;

import java.util.Optional;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineGetPayloadV3 extends AbstractEngineGetPayload {

  private static final Logger LOG = LoggerFactory.getLogger(EngineGetPayloadV3.class);
  private final Optional<ScheduledProtocolSpec.Hardfork> shanghai;
  private final Optional<ScheduledProtocolSpec.Hardfork> cancun;

  public EngineGetPayloadV3(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeMiningCoordinator,
      final BlockResultFactory blockResultFactory,
      final EngineCallListener engineCallListener,
      final ProtocolSchedule schedule) {
    super(vertx, protocolContext, mergeMiningCoordinator, blockResultFactory, engineCallListener);
    this.shanghai = schedule.hardforkFor(s -> s.fork().name().equalsIgnoreCase("Shanghai"));
    this.cancun = schedule.hardforkFor(s -> s.fork().name().equalsIgnoreCase("Cancun"));
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_GET_PAYLOAD_V3.getMethodName();
  }

  @Override
  protected JsonRpcResponse createResponse(
      final JsonRpcRequestContext request,
      final PayloadIdentifier payloadId,
      final BlockWithReceipts blockWithReceipts) {

    try {
      long builtAt = blockWithReceipts.getHeader().getTimestamp();

      if (this.shanghai.isPresent() && builtAt < this.shanghai.get().milestone()) {
        return new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            blockResultFactory.payloadTransactionCompleteV1(blockWithReceipts.getBlock()));
      } else if (this.shanghai.isPresent()
          && builtAt >= this.shanghai.get().milestone()
          && this.cancun.isPresent()
          && builtAt < this.cancun.get().milestone()) {
        return new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            blockResultFactory.payloadTransactionCompleteV2(blockWithReceipts));
      } else {
        return new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            blockResultFactory.payloadTransactionCompleteV3(blockWithReceipts));
      }

    } catch (ClassCastException e) {
      LOG.error("configuration error, can't call V3 endpoint with non-default protocol schedule");
      return new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.INTERNAL_ERROR);
    }
  }
}
