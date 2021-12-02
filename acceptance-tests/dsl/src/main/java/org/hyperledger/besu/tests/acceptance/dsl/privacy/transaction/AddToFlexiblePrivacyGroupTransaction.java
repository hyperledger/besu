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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.web3j.crypto.Credentials;
import org.web3j.utils.Base64String;

public class AddToFlexiblePrivacyGroupTransaction implements Transaction<String> {
  private final Base64String privacyGroupId;
  private final PrivacyNode adder;
  private final List<String> addresses;
  private final Credentials signer;

  public AddToFlexiblePrivacyGroupTransaction(
      final String privacyGroupId,
      final PrivacyNode adder,
      final Credentials signer,
      final PrivacyNode... nodes) {
    this.privacyGroupId = Base64String.wrap(privacyGroupId);
    this.adder = adder;
    this.signer = signer;
    this.addresses =
        Arrays.stream(nodes)
            .map(n -> n.getEnclave().getDefaultPublicKey())
            .collect(Collectors.toList());
  }

  @Override
  public String execute(final NodeRequests node) {
    try {
      return node.privacy().privxAddToPrivacyGroup(privacyGroupId, adder, signer, addresses);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
