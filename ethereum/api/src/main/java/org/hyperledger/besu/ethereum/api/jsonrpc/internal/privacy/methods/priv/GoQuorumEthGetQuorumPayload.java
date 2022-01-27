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

import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.GoQuorumEnclave;
import org.hyperledger.besu.enclave.types.GoQuorumReceiveResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoQuorumEthGetQuorumPayload implements JsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(GoQuorumEthGetQuorumPayload.class);

  private final GoQuorumEnclave enclave;

  public GoQuorumEthGetQuorumPayload(final GoQuorumEnclave enclave) {
    this.enclave = enclave;
  }

  @Override
  public String getName() {
    return RpcMethod.GOQUORUM_ETH_GET_QUORUM_PAYLOAD.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final String key = requestContext.getRequiredParameter(0, String.class);
    final Bytes bytes;
    try {
      bytes = Bytes.fromHexString(key);
    } catch (final IllegalArgumentException e) {
      LOG.debug("Enclave key contains invalid hex character {}", key);
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }
    if (bytes.size() != 64) {
      LOG.debug("Enclave key expected length 64, but length is {}", bytes.size());
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    try {
      final GoQuorumReceiveResponse receive = enclave.receive(bytes.toBase64String());
      return new JsonRpcSuccessResponse(
          requestContext.getRequest().getId(), Bytes.wrap(receive.getPayload()).toHexString());
    } catch (final EnclaveClientException ex) {
      if (ex.getStatusCode() == 404) {
        return new JsonRpcSuccessResponse(
            requestContext.getRequest().getId(), Bytes.EMPTY.toHexString());
      } else {
        LOG.debug("Error retrieving enclave payload: ", ex);
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), JsonRpcError.INTERNAL_ERROR);
      }
    }
  }
}
