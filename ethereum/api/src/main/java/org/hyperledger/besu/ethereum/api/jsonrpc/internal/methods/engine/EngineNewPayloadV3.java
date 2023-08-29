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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EngineExecutionPayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EngineNewPayloadRequestParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.Arrays;
import java.util.Optional;

import io.vertx.core.Vertx;

public class EngineNewPayloadV3 extends AbstractEngineNewPayload {

  private final Optional<ScheduledProtocolSpec.Hardfork> cancun;

  public EngineNewPayloadV3(
      final Vertx vertx,
      final ProtocolSchedule timestampSchedule,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator,
      final EthPeers ethPeers,
      final EngineCallListener engineCallListener) {
    super(
        vertx, timestampSchedule, protocolContext, mergeCoordinator, ethPeers, engineCallListener);
    this.cancun = timestampSchedule.hardforkFor(s -> s.fork().name().equalsIgnoreCase("Cancun"));
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_NEW_PAYLOAD_V3.getMethodName();
  }

  /**
   * Retrieves the new payload request parameters from the given JSON-RPC request context.
   *
   * @param requestContext the JSON-RPC request context
   * @return a new instance of EngineNewPayloadRequestParameter
   */
  public EngineNewPayloadRequestParameter getEngineNewPayloadRequestParams(
      final JsonRpcRequestContext requestContext) {

    final EngineExecutionPayloadParameter payload =
        requestContext.getRequiredParameter(0, EngineExecutionPayloadParameter.class);
    final String[] versionedHashes = requestContext.getRequiredParameter(1, String[].class);

    final String parentBeaconBlockRoot = requestContext.getRequiredParameter(2, String.class);

    return new EngineNewPayloadRequestParameter(
        payload,
        Optional.of(Arrays.stream(versionedHashes).toList()),
        Optional.of(parentBeaconBlockRoot));
  }

  @Override
  protected ValidationResult<RpcErrorType> validateParameters(
      final EngineNewPayloadRequestParameter params) {
    if (params.getExecutionPayload().getBlobGasUsed() == null
        || params.getExecutionPayload().getExcessBlobGas() == null) {
      return ValidationResult.invalid(RpcErrorType.INVALID_PARAMS, "Missing blob gas fields");
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
}
