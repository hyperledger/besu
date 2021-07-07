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
package org.hyperledger.besu.config.experimental;

import org.hyperledger.besu.config.GenesisAllocation;
import org.hyperledger.besu.config.JsonUtil;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Streams;

public class PrivacyGenesisConfigFile implements PrivacyGenesisConfigOptions {

  private final Map<String, GenesisAllocation> allocations;

  public PrivacyGenesisConfigFile(final Map<String, GenesisAllocation> allocations) {
    this.allocations = allocations;
  }

  public static PrivacyGenesisConfigFile fromConfig(final String jsonString) {
    final ObjectNode json = JsonUtil.objectNodeFromString(jsonString, false);

    final Map<String, GenesisAllocation> allocations =
        JsonUtil.getObjectNode(json, "alloc").stream()
            .flatMap(
                alloc ->
                    Streams.stream(alloc.fieldNames())
                        .map(
                            key ->
                                new GenesisAllocation(
                                    key, JsonUtil.getObjectNode(alloc, key).get())))
            .collect(Collectors.toMap(x -> x.getAddress().toLowerCase(Locale.ROOT), y -> y));

    return new PrivacyGenesisConfigFile(allocations);
  }

  @Override
  public Map<String, GenesisAllocation> getAllocations() {
    return this.allocations;
  }

  public static PrivacyGenesisConfigFile empty() {
    return new PrivacyGenesisConfigFile(Collections.emptyMap());
  }
}
