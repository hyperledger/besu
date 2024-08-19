/*
 * Copyright contributors to Hyperledger Besu.
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

import static org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId.CANCUN;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Vertx;

public class EngineNewPayloadV3 extends AbstractEngineNewPayload {

  private final Optional<Long> cancunMilestone;

  public EngineNewPayloadV3(
      final Vertx vertx,
      final ProtocolSchedule timestampSchedule,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator,
      final EthPeers ethPeers,
      final EngineCallListener engineCallListener) {
    super(
        vertx, timestampSchedule, protocolContext, mergeCoordinator, ethPeers, engineCallListener);
    this.cancunMilestone = timestampSchedule.milestoneFor(CANCUN);
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_NEW_PAYLOAD_V3.getMethodName();
  }

  @Override
  protected ValidationResult<RpcErrorType> validateParameters(
      final EnginePayloadParameter payloadParameter,
      final Optional<List<String>> maybeVersionedHashParam,
      final Optional<String> maybeBeaconBlockRootParam) {
    if (payloadParameter.getBlobGasUsed() == null) {
      return ValidationResult.invalid(
          RpcErrorType.INVALID_BLOB_GAS_USED_PARAMS, "Missing blob gas used field");
    } else if (payloadParameter.getExcessBlobGas() == null) {
      return ValidationResult.invalid(
          RpcErrorType.INVALID_EXCESS_BLOB_GAS_PARAMS, "Missing excess blob gas field");
    } else if (maybeVersionedHashParam == null || maybeVersionedHashParam.isEmpty()) {
      return ValidationResult.invalid(
          RpcErrorType.INVALID_VERSIONED_HASH_PARAMS, "Missing versioned hashes field");
    } else if (maybeBeaconBlockRootParam.isEmpty()) {
      return ValidationResult.invalid(
          RpcErrorType.INVALID_PARENT_BEACON_BLOCK_ROOT_PARAMS,
          "Missing parent beacon block root field");
    } else {
      return ValidationResult.valid();
    }
  }

  @Override
  protected ValidationResult<RpcErrorType> validateForkSupported(final long blockTimestamp) {
    return ForkSupportHelper.validateForkSupported(CANCUN, cancunMilestone, blockTimestamp);
  }
}
