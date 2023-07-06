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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing;

import org.hyperledger.besu.datatypes.Wei;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class TracingUtils {

  public static String dumpMemory(final Optional<Bytes32[]> maybeMemory) {
    return maybeMemory.map(TracingUtils::dumpMemory).orElse("");
  }

  private static String dumpMemory(final Bytes32[] memory) {
    return "0x".concat(dumpMemoryUnprefixed(memory));
  }

  private static String dumpMemoryUnprefixed(final Bytes32[] memory) {
    return Arrays.stream(memory).map(Bytes::toUnprefixedHexString).collect(Collectors.joining());
  }

  public static String dumpMemoryAndTrimTrailingZeros(final Bytes32[] memory) {
    final String memoryString = dumpMemoryUnprefixed(memory);
    final Bytes value = Bytes.fromHexString(memoryString);
    return "0x".concat(trimTrailingZeros(value).toUnprefixedHexString());
  }

  private static Bytes trimTrailingZeros(final Bytes value) {
    final int toTrim = trailingZeros(value);
    return value.slice(0, value.size() - toTrim + 1);
  }

  private static int trailingZeros(final Bytes bytes) {
    for (int i = bytes.size() - 1; i > 0; i--) {
      if (bytes.get(i) != 0) {
        return bytes.size() - i;
      }
    }
    return bytes.size();
  }

  public static String weiAsHex(final Wei balance) {
    return balance.isZero() ? "0x0" : balance.toShortHexString();
  }
}
