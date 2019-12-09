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

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.PrivateNonceProvider;
import org.hyperledger.besu.util.bytes.BytesValues;

public class PrivGetTransactionCount extends PrivacyApiMethod {

  private final PrivateNonceProvider privateNonceProvider;

  public PrivGetTransactionCount(
      final PrivacyParameters privacyParameters, final PrivateNonceProvider privateNonceProvider) {
    super(privacyParameters);
    this.privateNonceProvider = privateNonceProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_GET_TRANSACTION_COUNT.getMethodName();
  }

  @Override
  public JsonRpcResponse doResponse(final JsonRpcRequestContext requestContext) {
    if (requestContext.getRequest().getParamLength() != 2) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    final Address address = requestContext.getRequiredParameter(0, Address.class);
    final String privacyGroupId = requestContext.getRequiredParameter(1, String.class);

    final long nonce =
        privateNonceProvider.getNonce(address, BytesValues.fromBase64(privacyGroupId));
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), Quantity.create(nonce));
  }
}
