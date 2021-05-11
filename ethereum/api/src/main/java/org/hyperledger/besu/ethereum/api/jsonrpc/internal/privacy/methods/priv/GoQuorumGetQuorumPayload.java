/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import org.hyperledger.besu.enclave.GoQuorumEnclave;
import org.hyperledger.besu.enclave.types.GoQuorumReceiveResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.Base64;

import org.apache.tuweni.bytes.Bytes;

public class GoQuorumGetQuorumPayload implements JsonRpcMethod {
  private final GoQuorumEnclave enclave;

  public GoQuorumGetQuorumPayload(final GoQuorumEnclave enclave) {
    this.enclave = enclave;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_QUORUM_PAYLOAD.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Object id = requestContext.getRequest().getId();
    final String payloadHash = requestContext.getRequiredParameter(0, String.class);
    final String base64EncodedHash =
        Base64.getEncoder().encodeToString(Bytes.fromHexString(payloadHash).toArray());
    final GoQuorumReceiveResponse response = enclave.receive(base64EncodedHash);
    final Bytes privatePayload = Bytes.wrap(response.getPayload());
    return new JsonRpcSuccessResponse(id, privatePayload.toHexString());
  }
}
