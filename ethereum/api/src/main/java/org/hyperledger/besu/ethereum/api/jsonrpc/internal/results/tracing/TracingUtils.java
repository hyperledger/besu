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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing;

import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public class TracingUtils {

  public static String dumpMemory(final Optional<Bytes32[]> maybeMemory) {
    return maybeMemory.map(TracingUtils::dumpMemory).orElse("");
  }

  private static String dumpMemory(final Bytes32[] memory) {
    return "0x".concat(dumpMemoryUnprefixed(memory));
  }

  private static String dumpMemoryUnprefixed(final Bytes32[] memory) {
    return Arrays.stream(memory).map(BytesValue::toUnprefixedString).collect(Collectors.joining());
  }

  public static String dumpMemoryAndTrimTrailingZeros(final Bytes32[] memory) {
    final String memoryString = dumpMemoryUnprefixed(memory);
    final BytesValue value = BytesValue.fromHexString(memoryString);
    return "0x".concat(BytesValues.trimTrailingZeros(value).toUnprefixedString());
  }
}
