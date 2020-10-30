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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.network.exceptions.P2PDisabledException;

public abstract class AdminModifyPeer implements JsonRpcMethod {

  protected final P2PNetwork peerNetwork;

  protected AdminModifyPeer(final P2PNetwork peerNetwork) {
    this.peerNetwork = peerNetwork;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    if (requestContext.getRequest().getParamLength() != 1) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }
    try {
      final String enodeString = requestContext.getRequiredParameter(0, String.class);
      return performOperation(requestContext.getRequest().getId(), enodeString);
    } catch (final InvalidJsonRpcParameters e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    } catch (final IllegalArgumentException e) {
      if (e.getMessage()
          .endsWith(
              "Invalid node ID: node ID must have exactly 128 hexadecimal characters and should not include any '0x' hex prefix.")) {
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), JsonRpcError.ENODE_ID_INVALID);
      } else {
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), JsonRpcError.PARSE_ERROR);
      }
    } catch (final P2PDisabledException e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.P2P_DISABLED);
    }
  }

  protected abstract JsonRpcResponse performOperation(final Object id, final String enode);
}
