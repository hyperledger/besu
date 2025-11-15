/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.util;

import org.apache.tuweni.bytes.Bytes;

public class HexUtils {

  private static final char[] hexChars = "0123456789abcdef".toCharArray();

  /**
   * Optimized version of org.apache.tuweni.bytes.Bytes.toFastHex that avoids the megamorphic get
   * and size calls Adapted from #{@link
   * org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.StructLog#toCompactHex } but this
   * method retains the leading zeros
   */
  public static String toFastHex(final Bytes abytes, final boolean prefix) {
    byte[] bytes = abytes.toArrayUnsafe();
    final int size = bytes.length;
    final StringBuilder result = new StringBuilder(prefix ? (size * 2) + 2 : size * 2);

    if (prefix) {
      result.append("0x");
    }

    for (int i = 0; i < size; i++) {
      byte b = bytes[i];

      int highNibble = (b >> 4) & 0xF;
      result.append(hexChars[highNibble]);

      int lowNibble = b & 0xF;
      result.append(hexChars[lowNibble]);
    }

    return result.toString();
  }
}
