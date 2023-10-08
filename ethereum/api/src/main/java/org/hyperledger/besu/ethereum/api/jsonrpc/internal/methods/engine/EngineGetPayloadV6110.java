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
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.Optional;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineGetPayloadV6110 extends AbstractEngineGetPayload {

  private static final Logger LOG = LoggerFactory.getLogger(EngineGetPayloadV6110.class);
  private final Optional<ScheduledProtocolSpec.Hardfork> eip6110;

  public EngineGetPayloadV6110(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeMiningCoordinator,
      final BlockResultFactory blockResultFactory,
      final EngineCallListener engineCallListener) {
    super(vertx, protocolContext, mergeMiningCoordinator, blockResultFactory, engineCallListener);
    this.eip6110 = Optional.empty();
  }

  public EngineGetPayloadV6110(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeMiningCoordinator,
      final BlockResultFactory blockResultFactory,
      final EngineCallListener engineCallListener,
      final ProtocolSchedule schedule) {
    super(vertx, protocolContext, mergeMiningCoordinator, blockResultFactory, engineCallListener);
    this.eip6110 = schedule.hardforkFor(s -> s.fork().name().equalsIgnoreCase("ExperimentalEips"));
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_GET_PAYLOAD_V6110.getMethodName();
  }

  @Override
  protected JsonRpcResponse createResponse(
      final JsonRpcRequestContext request,
      final PayloadIdentifier payloadId,
      final BlockWithReceipts blockWithReceipts) {

    try {
      long builtAt = blockWithReceipts.getHeader().getTimestamp();

      if (eip6110.isPresent() && builtAt >= eip6110.get().milestone()) {
        return new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            blockResultFactory.payloadTransactionCompleteV6110(blockWithReceipts));
      } else {
        LOG.error("Timestamp of the built payload is less than EIP-6110 activation timestamp");
        return new JsonRpcErrorResponse(
            request.getRequest().getId(), RpcErrorType.UNSUPPORTED_FORK);
      }

    } catch (ClassCastException e) {
      LOG.error(
          "configuration error, can't call V6110 endpoint with non-default protocol schedule");
      return new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.INTERNAL_ERROR);
    }
  }

  @Override
  protected ValidationResult<RpcErrorType> validateForkSupported(final long blockTimestamp) {
    if (protocolSchedule.isPresent()) {
      if (eip6110.isPresent() && blockTimestamp >= eip6110.get().milestone()) {
        return ValidationResult.valid();
      } else {
        return ValidationResult.invalid(
            RpcErrorType.UNSUPPORTED_FORK,
            "EIP-6110 configured to start at timestamp: " + eip6110.get().milestone());
      }
    } else {
      return ValidationResult.invalid(
          RpcErrorType.UNSUPPORTED_FORK, "Configuration error, no schedule for EIP-6110 fork set");
    }
  }
}
