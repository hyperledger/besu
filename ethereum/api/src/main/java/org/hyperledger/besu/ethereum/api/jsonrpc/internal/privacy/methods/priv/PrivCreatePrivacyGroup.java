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
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
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
            parameter.getName(),
            parameter.getDescription());
    privateTransactionHandler.sendToOrion(privateTransaction);
    final Transaction privacyMarkerTransaction =
        privateTransactionHandler.createPrivacyMarkerTransaction(
            privacyParameters.getEnclavePublicKey(), privateTransaction);
    return transactionPool
        .addLocalTransaction(privacyMarkerTransaction)
        .either(
            () ->
                // TODO: return privacy group creation receipt which has PMT hash and PGID
                new JsonRpcSuccessResponse(
                        requestContext.getRequest().getId(), privacyMarkerTransaction.getHash().toString()),
            errorReason ->
                new JsonRpcErrorResponse(
                        requestContext.getRequest().getId(),
                    JsonRpcErrorConverter.convertTransactionInvalidReason(errorReason)));
  }

  private static BytesValue generateLegacyGroup(
      final BytesValue privateFrom, final List<BytesValue> privateFor) {
    final List<byte[]> stringList = new ArrayList<>();
    stringList.add(Base64.getDecoder().decode(privateFrom.toString()));
    privateFor.forEach(item -> stringList.add(item.getArrayUnsafe()));

    final BytesValueRLPOutput bytesValueRLPOutput = new BytesValueRLPOutput();
    bytesValueRLPOutput.startList();
    stringList.stream()
        .distinct()
        .sorted(Comparator.comparing(Arrays::hashCode))
        .forEach(e -> bytesValueRLPOutput.writeBytesValue(BytesValue.wrap(e)));
    bytesValueRLPOutput.endList();
    return bytesValueRLPOutput.encoded();
  }
}
