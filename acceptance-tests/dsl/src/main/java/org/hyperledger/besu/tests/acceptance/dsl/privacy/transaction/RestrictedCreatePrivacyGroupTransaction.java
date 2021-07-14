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

import org.web3j.protocol.besu.Besu;
import org.web3j.protocol.besu.response.privacy.PrivCreatePrivacyGroup;
import org.web3j.utils.Base64String;

public class RestrictedCreatePrivacyGroupTransaction implements Transaction<String> {
  private final String name;
  private final String description;
  private final List<Base64String> addresses;

  public RestrictedCreatePrivacyGroupTransaction(
      final String name, final String description, final PrivacyNode... nodes) {
    this.name = name;
    this.description = description;
    this.addresses =
        Arrays.stream(nodes)
            .map(n -> Base64String.wrap(n.getEnclave().getDefaultPublicKey()))
            .collect(Collectors.toList());
  }

  @Override
  public String execute(final NodeRequests node) {
    final Besu besu = node.privacy().getBesuClient();
    try {
      final PrivCreatePrivacyGroup result =
          besu.privCreatePrivacyGroup(addresses, name, description).send();
      if (result.hasError()) {
        throw new RuntimeException(result.getError().getMessage());
      } else {
        return result.getPrivacyGroupId().toString();
      }
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
