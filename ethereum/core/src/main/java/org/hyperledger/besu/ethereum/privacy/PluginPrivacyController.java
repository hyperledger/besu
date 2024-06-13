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
package org.hyperledger.besu.ethereum.privacy;

import static org.hyperledger.besu.ethereum.privacy.PrivateTransaction.readFrom;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.TransactionLocation;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.plugin.services.PrivacyPluginService;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class PluginPrivacyController extends AbstractPrivacyController {

  private final PrivacyPluginService privacyPluginService;

  public PluginPrivacyController(
      final Blockchain blockchain,
      final PrivacyParameters privacyParameters,
      final Optional<BigInteger> chainId,
      final PrivateTransactionSimulator privateTransactionSimulator,
      final PrivateNonceProvider privateNonceProvider,
      final PrivateWorldStateReader privateWorldStateReader) {
    super(
        blockchain,
        privacyParameters,
        chainId,
        privateTransactionSimulator,
        privateNonceProvider,
        privateWorldStateReader);
    this.privacyPluginService = privacyParameters.getPrivacyService();
  }

  @Override
  public String createPrivateMarkerTransactionPayload(
      final PrivateTransaction privateTransaction,
      final String privacyUserId,
      final Optional<PrivacyGroup> privacyGroup) {

    return privacyPluginService
        .getPayloadProvider()
        .generateMarkerPayload(privateTransaction, privacyUserId)
        .toBase64String();
  }

  @Override
  public Optional<ExecutedPrivateTransaction> findPrivateTransactionByPmtHash(
      final Hash pmtHash, final String enclaveKey) {

    final Optional<Transaction> transaction = blockchain.getTransactionByHash(pmtHash);

    if (transaction.isEmpty()) {
      return Optional.empty();
    }

    final TransactionLocation transactionLocation =
        blockchain.getTransactionLocation(pmtHash).orElseThrow();

    final BlockHeader blockHeader =
        blockchain.getBlockHeader(transactionLocation.getBlockHash()).orElseThrow();

    final Optional<org.hyperledger.besu.plugin.data.PrivateTransaction> pluginPrivateTransaction =
        privacyPluginService
            .getPayloadProvider()
            .getPrivateTransactionFromPayload(transaction.get());

    if (pluginPrivateTransaction.isEmpty()) {
      return Optional.empty();
    }

    final PrivateTransaction privateTransaction = readFrom(pluginPrivateTransaction.get());

    final String internalPrivacyGroupId =
        privateTransaction.determinePrivacyGroupId().toBase64String();

    final ExecutedPrivateTransaction executedPrivateTransaction =
        new ExecutedPrivateTransaction(
            blockHeader.getHash(),
            blockHeader.getNumber(),
            pmtHash,
            transactionLocation.getTransactionIndex(),
            internalPrivacyGroupId,
            privateTransaction);

    return Optional.of(executedPrivateTransaction);
  }

  @Override
  public PrivacyGroup createPrivacyGroup(
      final List<String> addresses,
      final String name,
      final String description,
      final String privacyUserId) {
    throw new PrivacyConfigurationNotSupportedException(
        "Method not supported when using PrivacyPlugin");
  }

  @Override
  public String deletePrivacyGroup(final String privacyGroupId, final String privacyUserId) {
    throw new PrivacyConfigurationNotSupportedException(
        "Method not supported when using PrivacyPlugin");
  }

  @Override
  public Optional<PrivacyGroup> findPrivacyGroupByGroupId(
      final String privacyGroupId, final String privacyUserId) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyGroupId, privacyUserId);

    return Optional.of(
        new PrivacyGroup(
            privacyGroupId,
            PrivacyGroup.Type.LEGACY,
            "PrivacyPlugin",
            "PrivacyPlugin",
            Collections.emptyList()));
  }

  @Override
  public PrivacyGroup[] findPrivacyGroupByMembers(
      final List<String> asList, final String privacyUserId) {
    throw new PrivacyConfigurationNotSupportedException(
        "Method not supported when using PrivacyPlugin");
  }

  @Override
  public void verifyPrivacyGroupContainsPrivacyUserId(
      final String privacyGroupId, final String privacyUserId, final Optional<Long> blockNumber) {
    if (!privacyPluginService
        .getPrivacyGroupAuthProvider()
        .canAccess(privacyGroupId, privacyUserId, blockNumber)) {
      throw new MultiTenancyValidationException(
          "Privacy group must contain the enclave public key");
    }
  }

  @Override
  public void verifyPrivacyGroupContainsPrivacyUserId(
      final String privacyGroupId, final String privacyUserId) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyGroupId, privacyUserId, Optional.empty());
  }
}
