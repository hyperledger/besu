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
import org.hyperledger.besu.crosschain.core.keys.BlsThresholdPublicKey;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.HashMap;
import java.util.Map;

/** Return the Blockchain Public key, if such a key exists. */
public class CrossBlockchainPublicKey implements JsonRpcMethod {
  private final CrosschainController crosschainController;

  public CrossBlockchainPublicKey(final CrosschainController crosschainController) {
    this.crosschainController = crosschainController;
  }

  @Override
  public String getName() {
    return RpcMethod.CROSS_GET_BLOCKCHAIN_PUBLIC_KEY.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    if (request.getParamLength() != 0) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }

    final Map<String, Object> response = new HashMap<>();
    BlsThresholdPublicKey credentials = this.crosschainController.getBlockchainPublicKey();

    response.put("pubkey", credentials.getEncodedPublicKey());

    return new JsonRpcSuccessResponse(request.getId(), response);
  }
}
