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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.miner;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinerSetMinPriorityFee implements JsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(MinerSetMinPriorityFee.class);

  private final MiningConfiguration miningConfiguration;

  public MinerSetMinPriorityFee(final MiningConfiguration miningConfiguration) {
    this.miningConfiguration = miningConfiguration;
  }

  @Override
  public String getName() {
    return RpcMethod.MINER_SET_MIN_PRIORITY_FEE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    try {
      final Wei minPriorityFeePerGas =
          Wei.fromHexString(requestContext.getRequiredParameter(0, String.class));
      miningConfiguration.setMinPriorityFeePerGas(minPriorityFeePerGas);
      LOG.debug(
          "min priority fee per gas changed to {}", minPriorityFeePerGas.toHumanReadableString());
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), true);
    } catch (final IllegalArgumentException | JsonRpcParameterException e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          new JsonRpcError(RpcErrorType.INVALID_MIN_PRIORITY_FEE_PARAMS, e.getMessage()));
    }
  }
}
