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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.eea;

import static org.hyperledger.besu.ethereum.privacy.PrivacyGroupUtil.findOnchainPrivacyGroup;

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class OnChainEeaSendRawTransaction extends EeaSendRawTransaction {

  public OnChainEeaSendRawTransaction(
      final TransactionPool transactionPool,
      final PrivacyController privacyController,
      final EnclavePublicKeyProvider enclavePublicKeyProvider) {
    super(transactionPool, privacyController, enclavePublicKeyProvider);
  }

  @Override
  Optional<PrivacyGroup> findPrivacyGroup(
      final Optional<Bytes> maybePrivacyGroupId,
      final String enclavePublicKey,
      final PrivateTransaction privateTransaction) {
    if (maybePrivacyGroupId.isEmpty()) {
      throw new JsonRpcErrorResponseException(JsonRpcError.ONCHAIN_PRIVACY_GROUP_ID_NOT_AVAILABLE);
    }
    final Optional<PrivacyGroup> maybePrivacyGroup =
        findOnchainPrivacyGroup(
            privacyController, maybePrivacyGroupId, enclavePublicKey, privateTransaction);
    if (maybePrivacyGroup.isEmpty()) {
      throw new JsonRpcErrorResponseException(JsonRpcError.ONCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST);
    }
    return maybePrivacyGroup;
  }

  @Override
  Transaction createPMT(
      final PrivateTransaction privateTransaction,
      final Optional<PrivacyGroup> maybePrivacyGroup,
      final Optional<Bytes> maybePrivacyGroupId,
      final String enclavePublicKey) {
    final String privateTransactionLookupId =
        privacyController.sendTransaction(privateTransaction, enclavePublicKey, maybePrivacyGroup);
    final Bytes privacyGroupId =
        maybePrivacyGroupId.get(); // exists, as it has been checked in findPrivacyGroup
    final Optional<String> addPayloadPrivateTransactionLookupId =
        privacyController.buildAndSendAddPayload(
            privateTransaction, Bytes32.wrap(privacyGroupId), enclavePublicKey);
    return privacyController.createPrivacyMarkerTransaction(
        buildCompoundLookupId(privateTransactionLookupId, addPayloadPrivateTransactionLookupId),
        privateTransaction,
        Address.ONCHAIN_PRIVACY);
  }

  private String buildCompoundLookupId(
      final String privateTransactionLookupId,
      final Optional<String> maybePrivateTransactionLookupId) {
    return maybePrivateTransactionLookupId.isPresent()
        ? Bytes.concatenate(
                Bytes.fromBase64String(privateTransactionLookupId),
                Bytes.fromBase64String(maybePrivateTransactionLookupId.get()))
            .toBase64String()
        : privateTransactionLookupId;
  }
}
