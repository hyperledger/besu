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
import static org.hyperledger.besu.ethereum.util.PrivacyUtil.generateLegacyGroup;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcEnclaveErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.parameters.CreatePrivacyGroupParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionHandler;
import org.hyperledger.besu.ethereum.privacy.privatetransaction.GroupCreationTransactionFactory;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;

public class PrivCreatePrivacyGroup extends PrivacyApiMethod {

  private static final Logger LOG = getLogger();
  private PrivateTransactionHandler privateTransactionHandler;
  private GroupCreationTransactionFactory groupCreationTransactionFactory;
  private TransactionPool transactionPool;

  public PrivCreatePrivacyGroup(
      final PrivacyParameters privacyParameters,
      final GroupCreationTransactionFactory groupCreationTransactionFactory,
      final PrivateTransactionHandler privateTransactionHandler,
      final TransactionPool transactionPool) {
    super(privacyParameters);
    this.groupCreationTransactionFactory = groupCreationTransactionFactory;
    this.privateTransactionHandler = privateTransactionHandler;
    this.transactionPool = transactionPool;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_CREATE_PRIVACY_GROUP.getMethodName();
  }

  @Override
  public JsonRpcResponse doResponse(final JsonRpcRequestContext requestContext) {
    LOG.trace("Executing {}", RpcMethod.PRIV_CREATE_PRIVACY_GROUP.getMethodName());

    final CreatePrivacyGroupParameter parameter =
        requestContext.getRequiredParameter(0, CreatePrivacyGroupParameter.class);

    LOG.trace(
        "Creating a privacy group with name {} and description {}",
        parameter.getName(),
        parameter.getDescription());

    // FIXME: enclave.generatePrivacyGroupId() ? or createPrivacyGroupId(keccak256(rlp(participants
    // + creatorNonce??))
    final BytesValue pgId =
        generateLegacyGroup(
            BytesValues.fromBase64(privacyParameters.getEnclavePublicKey()),
            Arrays.stream(parameter.getAddresses())
                .map(BytesValues::fromBase64)
                .collect(Collectors.toList()));

    final PrivateTransaction privateTransaction =
        groupCreationTransactionFactory.create(
            pgId,
            BytesValues.fromBase64(privacyParameters.getEnclavePublicKey()),
            Arrays.stream(parameter.getAddresses())
                .map(BytesValues::fromBase64)
                .collect(Collectors.toList()),
            Optional.ofNullable(parameter.getName()),
            Optional.ofNullable(parameter.getDescription()));

    final String transactionEnclaveKey;
    try {
      transactionEnclaveKey = privateTransactionHandler.sendToOrion(privateTransaction);
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          JsonRpcEnclaveErrorConverter.convertEnclaveInvalidReason(e.getMessage()));
    }

    final Transaction privacyMarkerTransaction =
        privateTransactionHandler.createPrivacyMarkerTransaction(
            transactionEnclaveKey, privateTransaction);
    LOG.info("privateTransaction " + privateTransaction.toString());
    LOG.info("Submitting privacy group creation request " + privacyMarkerTransaction.toString());
    LOG.info("Key is " + BytesValues.asBase64String(privacyMarkerTransaction.getPayload()));
    return transactionPool
        .addLocalTransaction(privacyMarkerTransaction)
        .either(
            () ->
                new JsonRpcSuccessResponse(
                    requestContext.getRequest().getId(),
                    new PrivCreatePrivacyGroupResponse(
                        BytesValues.asBase64String(pgId),
                        privacyMarkerTransaction.getHash().getHexString())),
            errorReason ->
                new JsonRpcErrorResponse(
                    requestContext.getRequest().getId(),
                    JsonRpcErrorConverter.convertTransactionInvalidReason(errorReason)));
  }
}
