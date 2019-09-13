/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.tests.acceptance.dsl.privacy.transaction;

import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.NodeRequests;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.web3j.protocol.pantheon.Pantheon;
import org.web3j.utils.Base64String;

public class CreatePrivacyGroupTransaction implements Transaction<String> {
  private final String name;
  private final String description;
  private final List<Base64String> addresses;

  public CreatePrivacyGroupTransaction(
      final String name, final String description, final PrivacyNode... nodes) {
    this.name = name;
    this.description = description;
    this.addresses =
        Arrays.stream(nodes)
            .map(n -> Base64String.wrap(n.getOrion().getDefaultPublicKey()))
            .collect(Collectors.toList());
  }

  @Override
  public String execute(final NodeRequests node) {
    final Pantheon pantheon = node.privacy().getPantheonClient();
    try {
      return pantheon
          .privCreatePrivacyGroup(addresses, name, description)
          .send()
          .getPrivacyGroupId()
          .toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
