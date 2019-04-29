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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.Quantity;
import tech.pegasys.pantheon.ethereum.privacy.PrivateStateStorage;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public class EeaGetTransactionCount implements JsonRpcMethod {

  private final JsonRpcParameter parameters;
  private final PrivateStateStorage privateStateStorage;
  private final WorldStateArchive privateWorldStateArchive;

  public EeaGetTransactionCount(
      final JsonRpcParameter parameters, final PrivacyParameters privacyParameters) {
    this.parameters = parameters;
    this.privateStateStorage = privacyParameters.getPrivateStateStorage();
    this.privateWorldStateArchive = privacyParameters.getPrivateWorldStateArchive();
  }

  @Override
  public String getName() {
    return "eea_getTransactionCount";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    if (request.getParamLength() != 2) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }

    final Address address = parameters.required(request.getParams(), 0, Address.class);
    final String privacyGroupId = parameters.required(request.getParams(), 1, String.class);

    return privateStateStorage
        .getPrivateAccountState(BytesValue.fromHexString(privacyGroupId))
        .map(
            lastRootHash -> {
              final MutableWorldState privateWorldState =
                  privateWorldStateArchive.getMutable(lastRootHash).get();

              final Account maybePrivateSender = privateWorldState.get(address);

              if (maybePrivateSender != null) {
                return new JsonRpcSuccessResponse(
                    request.getId(), Quantity.create(maybePrivateSender.getNonce()));
              }
              return new JsonRpcSuccessResponse(request.getId(), Quantity.create(0));
            })
        .orElse(new JsonRpcSuccessResponse(request.getId(), Quantity.create(0)));
  }
}
