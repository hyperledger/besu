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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadAttributesParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO Withdrawals use composition instead? Want to make it more obvious that there is no
// difference between V1/V2 code other than the method name
public class EngineForkchoiceUpdatedV2 extends AbstractEngineForkchoiceUpdated {

  private static final Logger LOG = LoggerFactory.getLogger(EngineForkchoiceUpdatedV2.class);

  public EngineForkchoiceUpdatedV2(
      final Vertx vertx,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator,
      final EngineCallListener engineCallListener) {
    super(vertx, protocolSchedule, protocolContext, mergeCoordinator, engineCallListener);
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_FORKCHOICE_UPDATED_V2.getMethodName();
  }

  @Override
  protected Optional<JsonRpcErrorResponse> isPayloadAttributesValid(
      final Object requestId,
      final EnginePayloadAttributesParameter payloadAttributes,
      final Optional<List<Withdrawal>> maybeWithdrawals,
      final BlockHeader headBlockHeader) {
    if (payloadAttributes.getTimestamp() >= cancunTimestamp) {
      if (payloadAttributes.getParentBeaconBlockRoot() == null
          || payloadAttributes.getParentBeaconBlockRoot().isEmpty()) {
        return Optional.of(new JsonRpcErrorResponse(requestId, RpcErrorType.UNSUPPORTED_FORK));
      } else {
        return Optional.of(new JsonRpcErrorResponse(requestId, RpcErrorType.INVALID_PARAMS));
      }
    } else if (payloadAttributes.getParentBeaconBlockRoot() != null
        || !payloadAttributes.getParentBeaconBlockRoot().isEmpty()) {
      LOG.error(
          "Parent beacon block root hash present in payload attributes before cancun hardfork");
      return Optional.of(new JsonRpcErrorResponse(requestId, RpcErrorType.INVALID_PARAMS));
    } else {
      return super.isPayloadAttributesValid(
          requestId, payloadAttributes, maybeWithdrawals, headBlockHeader);
    }
  }
}
