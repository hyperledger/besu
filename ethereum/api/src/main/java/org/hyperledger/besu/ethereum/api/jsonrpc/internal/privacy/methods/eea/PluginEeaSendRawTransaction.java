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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.util.Optional;

import io.vertx.ext.auth.User;

public class PluginEeaSendRawTransaction extends AbstractEeaSendRawTransaction {
  private final PrivacyController privacyController;
  private final PrivacyIdProvider privacyIdProvider;

  public PluginEeaSendRawTransaction(
      final TransactionPool transactionPool,
      final PrivacyController privacyController,
      final PrivacyIdProvider privacyIdProvider) {
    super(transactionPool);
    this.privacyController = privacyController;
    this.privacyIdProvider = privacyIdProvider;
  }

  @Override
  protected ValidationResult<TransactionInvalidReason> validatePrivateTransaction(
      final PrivateTransaction privateTransaction, final Optional<User> user) {

    final String privacyUserId = privacyIdProvider.getPrivacyUserId(user);

    return privacyController.validatePrivateTransaction(privateTransaction, privacyUserId);
  }

  @Override
  protected Transaction createPrivateMarkerTransaction(
      final PrivateTransaction privateTransaction, final Optional<User> user) {

    final String privacyUserId = privacyIdProvider.getPrivacyUserId(user);

    final String payloadFromPlugin =
        privacyController.createPrivateMarkerTransactionPayload(
            privateTransaction, privacyUserId, Optional.empty());

    return privacyController.createPrivateMarkerTransaction(
        payloadFromPlugin, privateTransaction, Address.PLUGIN_PRIVACY);
  }
}
