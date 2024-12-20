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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.DEFAULT_PRIVACY;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.util.NonceProvider;
import org.hyperledger.besu.plugin.data.Restriction;
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory;

import java.util.Optional;

import io.vertx.ext.auth.User;
import org.apache.tuweni.bytes.Bytes;

@Deprecated(since = "24.12.0")
public class RestrictedOffchainEeaSendRawTransaction extends AbstractEeaSendRawTransaction {

  final PrivacyController privacyController;
  private final PrivacyIdProvider privacyIdProvider;

  public RestrictedOffchainEeaSendRawTransaction(
      final TransactionPool transactionPool,
      final PrivacyIdProvider privacyIdProvider,
      final PrivateMarkerTransactionFactory privateMarkerTransactionFactory,
      final NonceProvider publicNonceProvider,
      final PrivacyController privacyController) {
    super(transactionPool, privacyIdProvider, privateMarkerTransactionFactory, publicNonceProvider);
    this.privacyIdProvider = privacyIdProvider;
    this.privacyController = privacyController;
  }

  @Override
  protected ValidationResult<TransactionInvalidReason> validatePrivateTransaction(
      final PrivateTransaction privateTransaction, final Optional<User> user) {

    if (!privateTransaction.getRestriction().equals(Restriction.RESTRICTED)) {
      return ValidationResult.invalid(
          TransactionInvalidReason.PRIVATE_UNIMPLEMENTED_TRANSACTION_TYPE);
    }

    final String privacyUserId = privacyIdProvider.getPrivacyUserId(user);

    if (!privateTransaction.getPrivateFrom().equals(Bytes.fromBase64String(privacyUserId))) {
      throw new JsonRpcErrorResponseException(PRIVATE_FROM_DOES_NOT_MATCH_ENCLAVE_PUBLIC_KEY);
    }

    return privacyController.validatePrivateTransaction(privateTransaction, privacyUserId);
  }

  @Override
  protected Transaction createPrivateMarkerTransaction(
      final Address sender,
      final PrivateTransaction privateTransaction,
      final Optional<User> user) {

    final String privacyUserId = privacyIdProvider.getPrivacyUserId(user);

    final Optional<PrivacyGroup> maybePrivacyGroup =
        privateTransaction
            .getPrivacyGroupId()
            .flatMap(
                privacyGroupId ->
                    privacyController.findPrivacyGroupByGroupId(
                        privacyGroupId.toBase64String(), privacyUserId));

    final String privateTransactionLookupId =
        privacyController.createPrivateMarkerTransactionPayload(
            privateTransaction, privacyUserId, maybePrivacyGroup);

    return createPrivateMarkerTransaction(
        sender, DEFAULT_PRIVACY, privateTransactionLookupId, privateTransaction, privacyUserId);
  }

  @Override
  protected long getGasLimit(final PrivateTransaction privateTransaction, final String pmtPayload) {
    return privateTransaction.getGasLimit();
  }
}
