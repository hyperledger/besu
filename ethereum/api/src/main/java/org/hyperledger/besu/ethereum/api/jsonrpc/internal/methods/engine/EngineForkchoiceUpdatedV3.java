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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EngineForkchoiceUpdatedParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadAttributesParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.Optional;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineForkchoiceUpdatedV3 extends AbstractEngineForkchoiceUpdated {

  private final Optional<ScheduledProtocolSpec.Hardfork> cancun;
  private static final Logger LOG = LoggerFactory.getLogger(EngineForkchoiceUpdatedV3.class);

  public EngineForkchoiceUpdatedV3(
      final Vertx vertx,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator,
      final EngineCallListener engineCallListener) {
    super(vertx, protocolSchedule, protocolContext, mergeCoordinator, engineCallListener);
    this.cancun = protocolSchedule.hardforkFor(s -> s.fork().name().equalsIgnoreCase("Cancun"));
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_FORKCHOICE_UPDATED_V3.getMethodName();
  }

  @Override
  protected ValidationResult<RpcErrorType> validateParameter(
      final EngineForkchoiceUpdatedParameter fcuParameter,
      final Optional<EnginePayloadAttributesParameter> maybePayloadAttributes) {
    if (fcuParameter.getHeadBlockHash() == null) {
      return ValidationResult.invalid(
          RpcErrorType.INVALID_PAYLOAD_ATTRIBUTES, "Missing head block hash");
    } else if (fcuParameter.getSafeBlockHash() == null) {
      return ValidationResult.invalid(
          RpcErrorType.INVALID_PAYLOAD_ATTRIBUTES, "Missing safe block hash");
    } else if (fcuParameter.getFinalizedBlockHash() == null) {
      return ValidationResult.invalid(
          RpcErrorType.INVALID_PAYLOAD_ATTRIBUTES, "Missing finalized block hash");
    }
    if (maybePayloadAttributes.isPresent()) {
      if (maybePayloadAttributes.get().getParentBeaconBlockRoot() == null) {
        return ValidationResult.invalid(
            RpcErrorType.INVALID_PAYLOAD_ATTRIBUTES, "Missing parent beacon block root hash");
      }
    }
    return ValidationResult.valid();
  }

  @Override
  protected ValidationResult<RpcErrorType> validateForkSupported(final long blockTimestamp) {
    if (protocolSchedule.isPresent()) {
      if (cancun.isPresent() && blockTimestamp >= cancun.get().milestone()) {
        return ValidationResult.valid();
      } else {
        return ValidationResult.invalid(
            RpcErrorType.UNSUPPORTED_FORK,
            "Cancun configured to start at timestamp: " + cancun.get().milestone());
      }
    } else {
      return ValidationResult.invalid(
          RpcErrorType.UNSUPPORTED_FORK, "Configuration error, no schedule for Cancun fork set");
    }
  }

  @Override
  protected Optional<JsonRpcErrorResponse> isPayloadAttributesValid(
      final Object requestId, final EnginePayloadAttributesParameter payloadAttributes) {
    if (payloadAttributes.getParentBeaconBlockRoot() == null) {
      LOG.error(
          "Parent beacon block root hash not present in payload attributes after cancun hardfork");
      return Optional.of(
          new JsonRpcErrorResponse(requestId, RpcErrorType.INVALID_PAYLOAD_ATTRIBUTES));
    } else if (payloadAttributes.getTimestamp() < cancun.get().milestone()) {
      return Optional.of(new JsonRpcErrorResponse(requestId, RpcErrorType.UNSUPPORTED_FORK));
    } else {
      return Optional.empty();
    }
  }
}
