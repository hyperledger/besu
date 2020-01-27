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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.parameters.CreatePrivacyGroupParameter;

public class PrivTransactions {
  public PrivGetPrivacyPrecompileAddressTransaction privGetPrivacyPrecompileAddress() {
    return new PrivGetPrivacyPrecompileAddressTransaction();
  }

  public PrivGetPrivateTransactionTransaction privGetPrivateTransaction(
      final String transactionHash) {
    return new PrivGetPrivateTransactionTransaction(transactionHash);
  }

  public PrivCreatePrivacyGroupTransaction privCreatePrivacyGroup(
      final CreatePrivacyGroupParameter params) {
    return new PrivCreatePrivacyGroupTransaction(params);
  }

  public PrivDeletePrivacyGroupTransaction privDeletePrivacyGroup(final String transactionHash) {
    return new PrivDeletePrivacyGroupTransaction(transactionHash);
  }

  public PrivFindPrivacyGroupTransaction privFindPrivacyGroup(final String[] groupMembers) {
    return new PrivFindPrivacyGroupTransaction(groupMembers);
  }
}
