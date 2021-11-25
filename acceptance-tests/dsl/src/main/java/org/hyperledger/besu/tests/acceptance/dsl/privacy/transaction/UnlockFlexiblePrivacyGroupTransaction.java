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

import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.utils.Base64String;

public class UnlockFlexiblePrivacyGroupTransaction implements Transaction<String> {
  private final Base64String privacyGroupId;
  private final PrivacyNode locker;
  private final Credentials signer;

  public UnlockFlexiblePrivacyGroupTransaction(
      final String privacyGroupId, final PrivacyNode locker, final Credentials signer) {
    this.privacyGroupId = Base64String.wrap(privacyGroupId);
    this.locker = locker;
    this.signer = signer;
  }

  @Override
  public String execute(final NodeRequests node) {
    try {
      return node.privacy().privxUnlockPrivacyGroup(locker, privacyGroupId, signer);
    } catch (final IOException | TransactionException e) {
      throw new RuntimeException(e);
    }
  }
}
