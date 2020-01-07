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

import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.web3j.protocol.besu.Besu;
import org.web3j.protocol.besu.response.privacy.PrivacyGroup;
import org.web3j.utils.Base64String;

public class FindPrivacyGroupTransaction implements Transaction<List<PrivacyGroup>> {
  private final List<Base64String> nodes;

  public FindPrivacyGroupTransaction(final List<String> nodeEnclaveKeys) {

    this.nodes = nodeEnclaveKeys.stream().map(Base64String::wrap).collect(Collectors.toList());
  }

  @Override
  public List<PrivacyGroup> execute(final NodeRequests node) {
    final Besu besu = node.privacy().getBesuClient();
    try {
      return besu.privFindPrivacyGroup(nodes).send().getGroups();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
