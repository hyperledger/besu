/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.p2p.discovery.dns;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

// Adapted from https://github.com/tmio/tuweni and licensed under Apache 2.0
/** Read Key value pairs from a DNS record */
public class KVReader {
  private KVReader() {}

  /**
   * Read a key value pair from a DNS record
   *
   * @param record the record to read
   * @return the key value pair
   */
  public static Map<String, String> readKV(final String record) {
    return Arrays.stream(record.split("\\s+"))
        .map(
            it -> {
              // if it contains an = or :, split into Map.entry from the first occurrence
              if (it.contains("=")) {
                return it.split("=", 2);
              } else if (it.contains(":")) {
                return it.split(":", 2);
              } else {
                // this should not happen, as the record should be well-formed
                return new String[] {it};
              }
            })
        .filter(kv -> kv.length == 2)
        .collect(Collectors.toMap(kv -> kv[0], kv -> kv[1]));
  }
}
