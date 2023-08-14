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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.Optional;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes32;

public class EngineNewPayloadV3 extends AbstractEngineNewPayload {

  private final Long cancunTimestamp;

  public EngineNewPayloadV3(
      final Vertx vertx,
      final ProtocolSchedule timestampSchedule,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator,
      final EthPeers ethPeers,
      final EngineCallListener engineCallListener) {
    super(
        vertx, timestampSchedule, protocolContext, mergeCoordinator, ethPeers, engineCallListener);
    Optional<ScheduledProtocolSpec.Hardfork> cancun =
        timestampSchedule.hardforkFor(s -> s.fork().name().equalsIgnoreCase("Cancun"));
    cancunTimestamp = cancun.map(ScheduledProtocolSpec.Hardfork::milestone).orElse(Long.MAX_VALUE);
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_NEW_PAYLOAD_V3.getMethodName();
  }

  @Override
  protected ValidationResult<RpcErrorType> validateForkSupported(
      final Object reqId,
      final EnginePayloadParameter payloadParameter,
      final Optional<Bytes32> maybeParentBeaconBlockRoot) {

    if (payloadParameter.getTimestamp() >= cancunTimestamp) {
      if (payloadParameter.getDataGasUsed() == null
          || payloadParameter.getExcessDataGas() == null) {
        return ValidationResult.invalid(RpcErrorType.INVALID_PARAMS, "Missing data gas fields");
      } else if (maybeParentBeaconBlockRoot.isEmpty()) {
        return ValidationResult.invalid(
            RpcErrorType.INVALID_PARAMS, "Missing parent beacon block root");
      } else {
        return ValidationResult.valid();
      }
    } else {
      return ValidationResult.invalid(RpcErrorType.INVALID_PARAMS, "Fork not supported");
    }
  }
  // TODO: I think we should check this, unless this is done somewhere else
  /*
    from https://github.com/ethereum/execution-apis/blob/main/src/engine/cancun.md#executionpayloadv3
    Given the expected array of blob versioned hashes client software MUST run its validation by taking the following steps:

  Obtain the actual array by concatenating blob versioned hashes lists (tx.blob_versioned_hashes) of each blob transaction included in the payload, respecting the order of inclusion. If the payload has no blob transactions the expected array MUST be [].
  Return {status: INVALID, latestValidHash: null, validationError: errorMessage | null} if the expected and the actual arrays don't match.

     */
}
