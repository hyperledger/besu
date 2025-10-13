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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
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

public class MinerSetMinGasPrice implements JsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(MinerSetMinGasPrice.class);

  private final MiningConfiguration miningConfiguration;

  public MinerSetMinGasPrice(final MiningConfiguration miningConfiguration) {
    this.miningConfiguration = miningConfiguration;
  }

  @Override
  public String getName() {
    return RpcMethod.MINER_SET_MIN_GAS_PRICE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    try {
      final Wei minGasPrice =
          Wei.fromHexString(requestContext.getRequiredParameter(0, String.class));
      miningConfiguration.setMinTransactionGasPrice(minGasPrice);
      LOG.debug("min gas price changed to {}", minGasPrice.toHumanReadableString());
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), true);
    } catch (final IllegalArgumentException invalidJsonRpcParameters) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          new JsonRpcError(
              RpcErrorType.INVALID_MIN_GAS_PRICE_PARAMS, invalidJsonRpcParameters.getMessage()));
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid min gas price parameter (index 0)",
          RpcErrorType.INVALID_MIN_GAS_PRICE_PARAMS,
          e);
    }
  }
}
