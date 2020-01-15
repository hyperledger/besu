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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.privx;

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.crypto.SecureRandomProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcEnclaveErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.parameters.CreatePrivacyGroupParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.SendTransactionResponse;
import org.hyperledger.besu.ethereum.privacy.groupcreation.GroupCreationTransactionFactory;

import java.security.SecureRandom;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class PrivxCreatePrivacyGroup implements JsonRpcMethod {

  private static final Logger LOG = getLogger();

  private final PrivacyController privacyController;
  private final GroupCreationTransactionFactory groupCreationTransactionFactory;
  private final EnclavePublicKeyProvider enclavePublicKeyProvider;
  private final SecureRandom secureRandom;
  private final TransactionPool transactionPool;

  public PrivxCreatePrivacyGroup(
      final PrivacyController privacyController,
      final GroupCreationTransactionFactory groupCreationTransactionFactory,
      final EnclavePublicKeyProvider enclavePublicKeyProvider,
      final TransactionPool transactionPool) {
    this.privacyController = privacyController;
    this.groupCreationTransactionFactory = groupCreationTransactionFactory;
    this.transactionPool = transactionPool;
    this.enclavePublicKeyProvider = enclavePublicKeyProvider;
    this.secureRandom = SecureRandomProvider.createSecureRandom();
  }

  @Override
  public String getName() {
    return RpcMethod.PRIVX_CREATE_PRIVACY_GROUP.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    LOG.trace("Executing {}", RpcMethod.PRIVX_CREATE_PRIVACY_GROUP.getMethodName());

    final CreatePrivacyGroupParameter parameter =
        requestContext.getRequiredParameter(0, CreatePrivacyGroupParameter.class);

    LOG.trace(
        "Creating a privacy group with name {} and description {}",
        parameter.getName(),
        parameter.getDescription());

    final byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    final Bytes privacyGroupId = Bytes.wrap(bytes);

    final Bytes enclavePublicKey =
        Bytes.fromBase64String(enclavePublicKeyProvider.getEnclaveKey(requestContext.getUser()));
    final PrivateTransaction privateTransaction =
        groupCreationTransactionFactory.create(
            privacyGroupId,
            enclavePublicKey,
            parameter.getAddresses().stream()
                .map(Bytes::fromBase64String)
                .collect(Collectors.toList()));

    final SendTransactionResponse sendTransactionResponse;
    try {
      sendTransactionResponse =
          privacyController.sendTransaction(privateTransaction, enclavePublicKey.toBase64String());
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason(e.getMessage()));
    }

    final Transaction privacyMarkerTransaction =
        privacyController.createPrivacyMarkerTransaction(
            sendTransactionResponse.getEnclaveKey(), privateTransaction);
    LOG.info("privateTransaction " + privateTransaction.toString());
    LOG.info("Submitting privacy group creation request " + privacyMarkerTransaction.toString());
    LOG.info("Key is " + privacyMarkerTransaction.getPayload().toBase64String());
    return transactionPool
        .addLocalTransaction(privacyMarkerTransaction)
        .either(
            () ->
                new JsonRpcSuccessResponse(
                    requestContext.getRequest().getId(),
                    new PrivCreatePrivacyGroupResponse(
                        privacyGroupId.toBase64String(),
                        privacyMarkerTransaction.getHash().toHexString())),
            errorReason ->
                new JsonRpcErrorResponse(
                    requestContext.getRequest().getId(),
                    JsonRpcErrorConverter.convertTransactionInvalidReason(errorReason)));
  }
}
