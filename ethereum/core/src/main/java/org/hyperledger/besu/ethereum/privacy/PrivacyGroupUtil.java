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
package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivacyGroupUtil {

  /*
   * Tessera and Besu both have code to generate the privacyGroupId for LEGACY AKA EEA groups.
   * Functionality relies on them both generating the same value. Tessera code is here:
   * https://github.com/ConsenSys/tessera/blob/c756e1bf2d1c7a7806cf3cb5b31361f51ad705f1/enclave/enclave-api/src/main/java/com/quorum/tessera/enclave/PrivacyGroupUtil.java
   */
  public static Bytes32 calculateEeaPrivacyGroupId(
      final Bytes privateFrom, final List<Bytes> privateFor) {
    final List<Bytes> privacyGroupMembers = new ArrayList<>();
    privacyGroupMembers.add(privateFrom);
    privacyGroupMembers.addAll(privateFor);

    final List<byte[]> sortedPublicEnclaveKeys =
        privacyGroupMembers.stream()
            .distinct()
            .map(Bytes::toArray)
            .sorted(Comparator.comparing(Arrays::hashCode))
            .collect(Collectors.toList());

    final BytesValueRLPOutput bytesValueRLPOutput = new BytesValueRLPOutput();
    bytesValueRLPOutput.writeList(
        sortedPublicEnclaveKeys,
        (privacyUserId, rlpOutput) -> rlpOutput.writeBytes(Bytes.of(privacyUserId)));

    return Hash.keccak256(bytesValueRLPOutput.encoded());
  }
}
