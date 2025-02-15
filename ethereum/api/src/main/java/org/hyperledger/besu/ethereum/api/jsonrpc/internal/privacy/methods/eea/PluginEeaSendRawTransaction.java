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

import static org.hyperledger.besu.ethereum.core.PrivacyParameters.PLUGIN_PRIVACY;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.util.NonceProvider;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory;

import java.util.Optional;

import io.vertx.ext.auth.User;
import org.apache.tuweni.bytes.Bytes;

@Deprecated(since = "24.12.0")
public class PluginEeaSendRawTransaction extends AbstractEeaSendRawTransaction {
  private final PrivacyController privacyController;
  private final PrivacyIdProvider privacyIdProvider;
  private final GasCalculator gasCalculator;

  public PluginEeaSendRawTransaction(
      final TransactionPool transactionPool,
      final PrivacyIdProvider privacyIdProvider,
      final PrivateMarkerTransactionFactory privateMarkerTransactionFactory,
      final NonceProvider publicNonceProvider,
      final PrivacyController privacyController,
      final GasCalculator gasCalculator) {
    super(transactionPool, privacyIdProvider, privateMarkerTransactionFactory, publicNonceProvider);
    this.privacyController = privacyController;
    this.privacyIdProvider = privacyIdProvider;
    this.gasCalculator = gasCalculator;
  }

  @Override
  protected ValidationResult<TransactionInvalidReason> validatePrivateTransaction(
      final PrivateTransaction privateTransaction, final Optional<User> user) {

    final String privacyUserId = privacyIdProvider.getPrivacyUserId(user);

    return privacyController.validatePrivateTransaction(privateTransaction, privacyUserId);
  }

  @Override
  protected Transaction createPrivateMarkerTransaction(
      final Address sender,
      final PrivateTransaction privateTransaction,
      final Optional<User> user) {

    final String privacyUserId = privacyIdProvider.getPrivacyUserId(user);

    final String payloadFromPlugin =
        privacyController.createPrivateMarkerTransactionPayload(
            privateTransaction, privacyUserId, Optional.empty());

    return createPrivateMarkerTransaction(
        sender, PLUGIN_PRIVACY, payloadFromPlugin, privateTransaction, privacyUserId);
  }

  @Override
  protected long getGasLimit(final PrivateTransaction privateTransaction, final String pmtPayload) {
    // The gas limit can not be determined by the sender because the payload could be changed by the
    // plugin
    // choose the highest of the two options
    return Math.max(
        privateTransaction.getGasLimit(),
        gasCalculator.transactionIntrinsicGasCost(Bytes.fromBase64String(pmtPayload), false, 0));
  }
}
