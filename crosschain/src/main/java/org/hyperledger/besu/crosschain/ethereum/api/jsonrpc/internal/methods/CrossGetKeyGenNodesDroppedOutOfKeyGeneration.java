/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.crosschain.core.CrosschainController;
import org.hyperledger.besu.crosschain.core.keys.generation.KeyGenFailureToCompleteReason;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.math.BigInteger;
import java.util.Map;

public class CrossGetKeyGenNodesDroppedOutOfKeyGeneration extends AbstractCrossWithKeyVersionParam {
  public CrossGetKeyGenNodesDroppedOutOfKeyGeneration(
      final CrosschainController crosschainController, final JsonRpcParameter parameters) {
    super(crosschainController, parameters);
  }

  @Override
  public String getName() {
    return RpcMethod.CROSS_GET_KEY_GEN_NODES_DROPPED_OUT_OF_KEY_GENERATION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    if (request.getParamLength() != 1) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }
    final long keyVersion = getKeyVersionParameter(request);

    Map<BigInteger, KeyGenFailureToCompleteReason> keyInfo =
        this.crosschainController.getKeyGenNodesDroppedOutOfKeyGeneration(keyVersion);
    LOG.trace("JSON RPC {}: Version: {}, Size: {}", getName(), keyVersion, keyInfo.size());
    return new JsonRpcSuccessResponse(request.getId(), keyInfo);
  }
}
