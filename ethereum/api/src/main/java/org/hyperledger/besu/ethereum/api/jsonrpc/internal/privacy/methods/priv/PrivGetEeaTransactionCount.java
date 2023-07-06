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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivacyGroupUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrivGetEeaTransactionCount implements JsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(PrivGetEeaTransactionCount.class);

  private final PrivacyController privacyController;
  private final PrivacyIdProvider privacyIdProvider;

  public PrivGetEeaTransactionCount(
      final PrivacyController privacyController, final PrivacyIdProvider privacyIdProvider) {
    this.privacyController = privacyController;
    this.privacyIdProvider = privacyIdProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_GET_EEA_TRANSACTION_COUNT.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    if (requestContext.getRequest().getParamLength() != 3) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    final Address address = requestContext.getRequiredParameter(0, Address.class);
    final String privateFrom = requestContext.getRequiredParameter(1, String.class);
    final String[] privateFor = requestContext.getRequiredParameter(2, String[].class);

    final String privacyUserId = privacyIdProvider.getPrivacyUserId(requestContext.getUser());

    if (!privateFrom.equals(privacyUserId)) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY);
    }

    try {
      final long nonce =
          determineEeaNonce(
              privateFrom,
              privateFor,
              address,
              privacyIdProvider.getPrivacyUserId(requestContext.getUser()));
      return new JsonRpcSuccessResponse(
          requestContext.getRequest().getId(), Quantity.create(nonce));
    } catch (final MultiTenancyValidationException e) {
      LOG.error("Unauthorized privacy multi-tenancy rpc request. {}", e.getMessage());
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), GET_PRIVATE_TRANSACTION_NONCE_ERROR);
    } catch (final Exception e) {
      LOG.error(e.getMessage(), e);
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), GET_PRIVATE_TRANSACTION_NONCE_ERROR);
    }
  }

  private long determineEeaNonce(
      final String privateFrom,
      final String[] privateFor,
      final Address address,
      final String privacyUserId) {

    final Bytes from = Bytes.fromBase64String(privateFrom);
    final List<Bytes> toAddresses =
        Arrays.stream(privateFor).map(Bytes::fromBase64String).collect(Collectors.toList());

    final Bytes32 privacyGroupId = PrivacyGroupUtil.calculateEeaPrivacyGroupId(from, toAddresses);
    return privacyController.determineNonce(
        address, privacyGroupId.toBase64String(), privacyUserId);
  }
}
