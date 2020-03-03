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
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivGetCodeTransaction;

import java.util.List;

public class PrivacyTransactions {

  public PrivGetTransactionReceiptTransaction getPrivateTransactionReceipt(
      final String transactionHash) {
    return new PrivGetTransactionReceiptTransaction(transactionHash);
  }

  public CreatePrivacyGroupTransaction createPrivacyGroup(
      final String name, final String description, final PrivacyNode... nodes) {
    return new CreatePrivacyGroupTransaction(name, description, nodes);
  }

  public FindPrivacyGroupTransaction findPrivacyGroup(final List<String> nodes) {
    return new FindPrivacyGroupTransaction(nodes);
  }

  public PrivDistributeTransactionTransaction privDistributeTransaction(
      final String signedPrivateTransaction) {
    return new PrivDistributeTransactionTransaction(signedPrivateTransaction);
  }

  public PrivGetCodeTransaction privGetCode(
      final String privacyGroupId, final Address contractAddress, final String blockParameter) {
    return new PrivGetCodeTransaction(privacyGroupId, contractAddress, blockParameter);
  }
}
