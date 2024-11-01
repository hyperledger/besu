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

import java.nio.charset.StandardCharsets;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinerSetExtraData implements JsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(MinerSetExtraData.class);

  private final MiningConfiguration miningConfiguration;

  public MinerSetExtraData(final MiningConfiguration miningConfiguration) {
    this.miningConfiguration = miningConfiguration;
  }

  @Override
  public String getName() {
    return RpcMethod.MINER_SET_EXTRA_DATA.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    try {
      final String rawParam = requestContext.getRequiredParameter(0, String.class);
      Bytes32.fromHexStringLenient(
          rawParam); // done for validation, we want a hex string and max 32 bytes
      final var extraData = Bytes.fromHexStringLenient(rawParam);
      miningConfiguration.setExtraData(extraData);
      LOG.atDebug()
          .setMessage("set extra data, raw=[{}] parsed=[{}], UTF-8=[{}]")
          .addArgument(rawParam)
          .addArgument(extraData::toHexString)
          .addArgument(() -> new String(extraData.toArray(), StandardCharsets.UTF_8))
          .log();
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), true);
    } catch (IllegalArgumentException | JsonRpcParameterException e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          new JsonRpcError(RpcErrorType.INVALID_EXTRA_DATA_PARAMS, e.getMessage()));
    }
  }
}
