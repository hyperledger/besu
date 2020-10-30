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
package org.hyperledger.besu.tests.acceptance.dsl.privacy.transaction;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.condition.PrivGetTransactionReceiptTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.util.LogFilterJsonParameter;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.EeaSendRawTransactionTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivCallTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivGetCodeTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivGetLogsTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivGetTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.filter.PrivGetFilterChangesTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.filter.PrivGetFilterLogsTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.filter.PrivNewFilterTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.filter.PrivUninstallFilterTransaction;

import java.util.List;

import org.web3j.crypto.Credentials;
import org.web3j.tx.Contract;

public class PrivacyTransactions {

  public PrivGetTransactionReceiptTransaction getPrivateTransactionReceipt(
      final String transactionHash) {
    return new PrivGetTransactionReceiptTransaction(transactionHash);
  }

  public CreatePrivacyGroupTransaction createPrivacyGroup(
      final String name, final String description, final PrivacyNode... nodes) {
    return new CreatePrivacyGroupTransaction(name, description, nodes);
  }

  public CreateOnChainPrivacyGroupTransaction createOnChainPrivacyGroup(
      final PrivacyNode creator,
      final String privateFrom,
      final List<String> addresses,
      final String token) {
    creator.getBesu().useAuthenticationTokenInHeaderForJsonRpc(token);
    return new CreateOnChainPrivacyGroupTransaction(creator, privateFrom, addresses);
  }

  public CreateOnChainPrivacyGroupTransaction createOnChainPrivacyGroup(
      final PrivacyNode creator, final String privateFrom, final List<String> addresses) {
    return new CreateOnChainPrivacyGroupTransaction(creator, privateFrom, addresses);
  }

  public AddToOnChainPrivacyGroupTransaction addToPrivacyGroup(
      final String privacyGroupId,
      final PrivacyNode adder,
      final Credentials signer,
      final PrivacyNode... nodes) {
    return new AddToOnChainPrivacyGroupTransaction(privacyGroupId, adder, signer, nodes);
  }

  public LockOnChainPrivacyGroupTransaction privxLockPrivacyGroupAndCheck(
      final String privacyGroupId, final PrivacyNode locker, final Credentials signer) {
    return new LockOnChainPrivacyGroupTransaction(privacyGroupId, locker, signer);
  }

  public FindPrivacyGroupTransaction findPrivacyGroup(final List<String> nodes) {
    return new FindPrivacyGroupTransaction(nodes);
  }

  public FindOnChainPrivacyGroupTransaction findOnChainPrivacyGroup(final List<String> nodes) {
    return new FindOnChainPrivacyGroupTransaction(nodes);
  }

  public PrivDistributeTransactionTransaction privDistributeTransaction(
      final String signedPrivateTransaction) {
    return new PrivDistributeTransactionTransaction(signedPrivateTransaction);
  }

  public PrivCallTransaction privCall(
      final String privacyGroupId, final Contract contract, final String encoded) {
    return new PrivCallTransaction(privacyGroupId, contract, encoded);
  }

  public PrivGetTransaction privGetTransaction(final String transactionHash) {
    return new PrivGetTransaction(transactionHash);
  }

  public PrivGetCodeTransaction privGetCode(
      final String privacyGroupId, final Address contractAddress, final String blockParameter) {
    return new PrivGetCodeTransaction(privacyGroupId, contractAddress, blockParameter);
  }

  public PrivGetLogsTransaction privGetLogs(
      final String privacyGroupId, final LogFilterJsonParameter filterParameter) {
    return new PrivGetLogsTransaction(privacyGroupId, filterParameter);
  }

  public RemoveFromOnChainPrivacyGroupTransaction removeFromPrivacyGroup(
      final String privacyGroupId,
      final String remover,
      final Credentials signer,
      final String memberToRemove) {
    return new RemoveFromOnChainPrivacyGroupTransaction(
        privacyGroupId, remover, signer, memberToRemove);
  }

  public EeaSendRawTransactionTransaction sendRawTransaction(final String transaction) {
    return new EeaSendRawTransactionTransaction(transaction);
  }

  public PrivNewFilterTransaction newFilter(
      final String privacyGroupId, final LogFilterJsonParameter filterParameter) {
    return new PrivNewFilterTransaction(privacyGroupId, filterParameter);
  }

  public PrivUninstallFilterTransaction uninstallFilter(
      final String privacyGroupId, final String filterId) {
    return new PrivUninstallFilterTransaction(privacyGroupId, filterId);
  }

  public PrivGetFilterLogsTransaction getFilterLogs(
      final String privacyGroupId, final String filterId) {
    return new PrivGetFilterLogsTransaction(privacyGroupId, filterId);
  }

  public PrivGetFilterChangesTransaction getFilterChanges(
      final String privacyGroupId, final String filterId) {
    return new PrivGetFilterChangesTransaction(privacyGroupId, filterId);
  }
}
