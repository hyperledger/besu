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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.HashMap;
import java.util.Map;

/** Return the Blockchain Public key, if such a key exists. */
public class CrossGetBlockchainPublicKey extends AbstractCrossWithKeyVersionParam {

  public CrossGetBlockchainPublicKey(
      final CrosschainController crosschainController, final JsonRpcParameter parameters) {
    super(crosschainController, parameters);
  }

  @Override
  public String getName() {
    return RpcMethod.CROSS_GET_BLOCKCHAIN_PUBLIC_KEY.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    int numParams = request.getParamLength();
    if (!(numParams == 0 || numParams == 1)) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }
    BytesValue encodedPublicKey;
    if (numParams == 1) {
      long keyVersion = getKeyVersionParameter(request);
      BlsThresholdPublicKey publicKey =
          this.crosschainController.getBlockchainPublicKey(keyVersion);
      encodedPublicKey = publicKey.getEncodedPublicKey();
      LOG.trace(
          "JSON RPC {}: Encoded public key: {}, Key version: {}",
          getName(),
          encodedPublicKey,
          keyVersion);
    } else {
      BlsThresholdPublicKey publicKey = this.crosschainController.getBlockchainPublicKey();
      encodedPublicKey = publicKey.getEncodedPublicKey();
      LOG.trace(
          "JSON RPC {}: Encoded public key: {}, Key Version: Current", getName(), encodedPublicKey);
    }
    final Map<String, Object> response = new HashMap<>();
    response.put("pubkey", encodedPublicKey);
    return new JsonRpcSuccessResponse(request.getId(), response);
  }
}
