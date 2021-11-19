/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.privacy;

import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY_PROXY;
import static org.hyperledger.besu.ethereum.privacy.group.FlexibleGroupManagement.ADD_PARTICIPANTS_METHOD_SIGNATURE;

import org.hyperledger.besu.datatypes.Address;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class FlexibleUtil {

  private FlexibleUtil() {}

  public static boolean isGroupAdditionTransaction(final PrivateTransaction privateTransaction) {
    final Optional<Address> to = privateTransaction.getTo();
    return to.isPresent()
        && to.get().equals(FLEXIBLE_PRIVACY_PROXY)
        && privateTransaction
            .getPayload()
            .toHexString()
            .startsWith(ADD_PARTICIPANTS_METHOD_SIGNATURE.toHexString());
  }

  public static List<String> getParticipantsFromParameter(final Bytes input) {
    final List<String> participants = new ArrayList<>();
    final Bytes mungedParticipants = input.slice(4 + 32 + 32);
    for (int i = 0; i <= mungedParticipants.size() - 32; i += 32) {
      participants.add(mungedParticipants.slice(i, 32).toBase64String());
    }
    return participants;
  }
}
