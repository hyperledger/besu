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

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadAttributesParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

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
      final Object requestId, final EnginePayloadAttributesParameter payloadAttributes) {

    if (payloadAttributes.getParentBeaconBlockRoot() != null) {
      LOG.error(
          "Parent beacon block root hash present in payload attributes before Cancun hardfork");
      return Optional.of(new JsonRpcErrorResponse(requestId, getInvalidPayloadAttributesError()));
    }

    return Optional.empty();
  }

  @Override
  protected ValidationResult<RpcErrorType> validateForkSupported(final long blockTimestamp) {
    if (cancunMilestone.isPresent() && blockTimestamp >= cancunMilestone.get()) {
      return ValidationResult.invalid(RpcErrorType.UNSUPPORTED_FORK);
    }

    return ValidationResult.valid();
  }
}
