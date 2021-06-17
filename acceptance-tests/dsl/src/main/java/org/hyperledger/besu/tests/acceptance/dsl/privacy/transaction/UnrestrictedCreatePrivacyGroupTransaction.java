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

import org.hyperledger.besu.ethereum.privacy.PrivacyGroupUtil;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.utils.Base64String;

public class UnrestrictedCreatePrivacyGroupTransaction implements Transaction<String> {

  private final List<Base64String> addresses;

  public UnrestrictedCreatePrivacyGroupTransaction(final PrivacyNode... nodes) {
    this.addresses =
        Arrays.stream(nodes)
            .map(n -> Base64String.wrap(n.getEnclave().getDefaultPublicKey()))
            .collect(Collectors.toList());
  }

  @Override
  public String execute(final NodeRequests node) {
    final List<Bytes> privateFor =
        addresses.stream().skip(1).map(x -> Bytes.wrap(x.raw())).collect(Collectors.toList());

    final Bytes32 privacyGroupId =
        PrivacyGroupUtil.calculateEeaPrivacyGroupId(Bytes.wrap(addresses.get(0).raw()), privateFor);

    return privacyGroupId.toBase64String();
  }
}
