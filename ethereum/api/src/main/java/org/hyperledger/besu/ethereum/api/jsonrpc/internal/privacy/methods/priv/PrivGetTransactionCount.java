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

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.FindPrivacyGroupRequest;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionHandler;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.Arrays;

public class PrivGetTransactionCount implements JsonRpcMethod {

  private static final Logger LOG = getLogger();

  private final JsonRpcParameter parameters;
  private final PrivateTransactionHandler privateTransactionHandler;
  private final Enclave enclave;

  public PrivGetTransactionCount(
      final JsonRpcParameter parameters,
      final PrivateTransactionHandler privateTransactionHandler,
      final Enclave enclave) {
    this.parameters = parameters;
    this.privateTransactionHandler = privateTransactionHandler;
    this.enclave = enclave;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_GET_TRANSACTION_COUNT.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    if (request.getParamLength() == 2) {
      final Address address = parameters.required(request.getParams(), 0, Address.class);
      final String privacyGroupId = parameters.required(request.getParams(), 1, String.class);

      final long nonce = privateTransactionHandler.getSenderNonce(address, privacyGroupId);
      return new JsonRpcSuccessResponse(request.getId(), Quantity.create(nonce));
    } else if (request.getParamLength() == 3) {
      final Address address = parameters.required(request.getParams(), 0, Address.class);
      final String privateFrom = parameters.required(request.getParams(), 1, String.class);
      final String[] privateFor = parameters.required(request.getParams(), 2, String[].class);

      try {
        final long nonce = determineNonce(privateFrom, privateFor, address);
        return new JsonRpcSuccessResponse(request.getId(), Quantity.create(nonce));
      } catch (final Exception e) {
        LOG.error(e.getMessage(), e);
        return new JsonRpcErrorResponse(
            request.getId(), JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR);
      }
    } else {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }
  }

  private long determineNonce(
      final String privateFrom, final String[] privateFor, final Address address) {

    final String[] groupMembers = Arrays.append(privateFor, privateFrom);

    final FindPrivacyGroupRequest request = new FindPrivacyGroupRequest(groupMembers);
    final List<PrivacyGroup> matchingGroups = Lists.newArrayList(enclave.findPrivacyGroup(request));

    final List<PrivacyGroup> legacyGroups =
        matchingGroups.stream()
            .filter(group -> group.getType() == PrivacyGroup.Type.LEGACY)
            .collect(Collectors.toList());

    if (legacyGroups.size() == 0) {
      // the legacy group does not exist yet
      return 0;
    }

    if (legacyGroups.size() != 1) {
      throw new RuntimeException(
          String.format(
              "Found invalid number of privacy groups (%d), expected 1.", legacyGroups.size()));
    }

    final String privacyGroupId = legacyGroups.get(0).getPrivacyGroupId();

    return privateTransactionHandler.getSenderNonce(address, privacyGroupId);
  }
}
