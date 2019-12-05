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
package org.hyperledger.besu.ethereum.util;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

public class PrivacyUtil {

  public static String getPrivacyGroup(final PrivateTransaction privateTransaction) {
    if (privateTransaction.getPrivacyGroupId().isPresent()) {
      return BytesValues.asBase64String(privateTransaction.getPrivacyGroupId().get());
    }
    return BytesValues.asBase64String(
        generateLegacyGroup(
            privateTransaction.getPrivateFrom(), privateTransaction.getPrivateFor().get()));
  }

  public static BytesValue generateLegacyGroup(
      final BytesValue privateFrom, final List<BytesValue> privateFor) {
    final List<byte[]> stringList = new ArrayList<>();
    stringList.add(Base64.getDecoder().decode(BytesValues.asBase64String(privateFrom)));
    privateFor.forEach(item -> stringList.add(item.getArrayUnsafe()));

    final BytesValueRLPOutput bytesValueRLPOutput = new BytesValueRLPOutput();
    bytesValueRLPOutput.startList();
    stringList.stream()
        .distinct()
        .sorted(Comparator.comparing(Arrays::hashCode))
        .forEach(e -> bytesValueRLPOutput.writeBytesValue(BytesValue.wrap(e)));
    bytesValueRLPOutput.endList();
    return Hash.hash(bytesValueRLPOutput.encoded());
  }
}
