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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.privacy.methods.eea;

import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.FindPrivacyGroupRequest;
import tech.pegasys.pantheon.enclave.types.PrivacyGroup;
import tech.pegasys.pantheon.enclave.types.PrivacyGroup.Type;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionHandler;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.bouncycastle.util.Arrays;

public class EeaPrivateNonceProvider {

  private final Enclave enclave;
  private final PrivateTransactionHandler privateTransactionHandler;

  public EeaPrivateNonceProvider(
      final Enclave enclave, final PrivateTransactionHandler privateTransactionHandler) {
    this.enclave = enclave;
    this.privateTransactionHandler = privateTransactionHandler;
  }

  public long determineNonce(
      final String privateFrom, final String[] privateFor, final Address address) {

    final String[] groupMembers = Arrays.append(privateFor, privateFrom);

    final FindPrivacyGroupRequest request = new FindPrivacyGroupRequest(groupMembers);
    final List<PrivacyGroup> matchingGroups = Lists.newArrayList(enclave.findPrivacyGroup(request));

    final List<PrivacyGroup> legacyGroups =
        matchingGroups.stream()
            .filter(group -> group.getType() == Type.LEGACY)
            .collect(Collectors.toList());

    if (legacyGroups.size() == 0) {
      // the legacy group does not exist yet
      return 0;
    }

    if (legacyGroups.size() != 1) {
      throw new RuntimeException(
          String.format(
              "Found invalid number of privacy groups (%d), expected 1.", legacyGroups.size()));
    }

    final String privacyGroupId = legacyGroups.get(0).getPrivacyGroupId();

    return privateTransactionHandler.getSenderNonce(address, privacyGroupId);
  }
}
