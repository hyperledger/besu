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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.privacy.PrivacyGroupUtil;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.condition.PrivGetTransactionReceiptTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.util.LogFilterJsonParameter;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.EeaSendRawTransactionTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivCallTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivDebugGetStateRoot;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivGetCodeTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivGetLogsTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivGetTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.filter.PrivGetFilterChangesTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.filter.PrivGetFilterLogsTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.filter.PrivNewFilterTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.filter.PrivUninstallFilterTransaction;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.tx.Contract;

public class PrivacyTransactions {

  public PrivGetTransactionReceiptTransaction getPrivateTransactionReceipt(
      final String transactionHash) {
    return new PrivGetTransactionReceiptTransaction(transactionHash);
  }

  public RestrictedCreatePrivacyGroupTransaction createPrivacyGroup(
      final String name, final String description, final PrivacyNode... nodes) {
    return new RestrictedCreatePrivacyGroupTransaction(name, description, nodes);
  }

  public CreateFlexiblePrivacyGroupTransaction createFlexiblePrivacyGroup(
      final PrivacyNode creator,
      final String privateFrom,
      final List<String> addresses,
      final String token) {
    creator.getBesu().useAuthenticationTokenInHeaderForJsonRpc(token);
    return new CreateFlexiblePrivacyGroupTransaction(creator, privateFrom, addresses);
  }

  public CreateFlexiblePrivacyGroupTransaction createFlexiblePrivacyGroup(
      final PrivacyNode creator, final String privateFrom, final List<String> addresses) {
    return new CreateFlexiblePrivacyGroupTransaction(creator, privateFrom, addresses);
  }

  public AddToFlexiblePrivacyGroupTransaction addToPrivacyGroup(
      final String privacyGroupId,
      final PrivacyNode adder,
      final Credentials signer,
      final PrivacyNode... nodes) {
    return new AddToFlexiblePrivacyGroupTransaction(privacyGroupId, adder, signer, nodes);
  }

  public LockFlexiblePrivacyGroupTransaction privxLockPrivacyGroupAndCheck(
      final String privacyGroupId, final PrivacyNode locker, final Credentials signer) {
    return new LockFlexiblePrivacyGroupTransaction(privacyGroupId, locker, signer);
  }

  public UnlockFlexiblePrivacyGroupTransaction privxUnlockPrivacyGroupAndCheck(
      final String privacyGroupId, final PrivacyNode locker, final Credentials signer) {
    return new UnlockFlexiblePrivacyGroupTransaction(privacyGroupId, locker, signer);
  }

  public FindPrivacyGroupTransaction findPrivacyGroup(final List<String> nodes) {
    return new FindPrivacyGroupTransaction(nodes);
  }

  public FindFlexiblePrivacyGroupTransaction findFlexiblePrivacyGroup(final List<String> nodes) {
    return new FindFlexiblePrivacyGroupTransaction(nodes);
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

  public RemoveFromFlexiblePrivacyGroupTransaction removeFromPrivacyGroup(
      final String privacyGroupId,
      final String remover,
      final Credentials signer,
      final String memberToRemove) {
    return new RemoveFromFlexiblePrivacyGroupTransaction(
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

  public PrivDebugGetStateRoot debugGetStateRoot(
      final String privacyGroupId, final String blockParam) {
    return new PrivDebugGetStateRoot(privacyGroupId, blockParam);
  }

  public String getLegacyPrivacyGroupId(final String privateFrom, final String... privateFor) {

    final Bytes32 privacyGroupId =
        PrivacyGroupUtil.calculateEeaPrivacyGroupId(
            Bytes.fromBase64String(privateFrom),
            Arrays.stream(privateFor).map(Bytes::fromBase64String).collect(Collectors.toList()));

    return privacyGroupId.toBase64String();
  }
}
