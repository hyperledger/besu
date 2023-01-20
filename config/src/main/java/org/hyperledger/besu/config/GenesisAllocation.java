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
package org.hyperledger.besu.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** The Genesis allocation configuration. */
public class GenesisAllocation {
  private final String address;
  private final ObjectNode data;

  /**
   * Instantiates a new Genesis allocation.
   *
   * @param address the address
   * @param data the data
   */
  GenesisAllocation(final String address, final ObjectNode data) {
    this.address = address;
    this.data = data;
  }

  /**
   * Gets address.
   *
   * @return the address
   */
  public String getAddress() {
    return address;
  }

  /**
   * Gets private key.
   *
   * @return the private key
   */
  public Optional<String> getPrivateKey() {
    return Optional.ofNullable(JsonUtil.getString(data, "privatekey", null));
  }

  /**
   * Gets balance.
   *
   * @return the balance
   */
  public String getBalance() {
    return JsonUtil.getValueAsString(data, "balance", "0");
  }

  /**
   * Gets code.
   *
   * @return the code
   */
  public String getCode() {
    return JsonUtil.getString(data, "code", null);
  }

  /**
   * Gets nonce.
   *
   * @return the nonce
   */
  public String getNonce() {
    return JsonUtil.getValueAsString(data, "nonce", "0");
  }

  /**
   * Gets version.
   *
   * @return the version
   */
  public String getVersion() {
    return JsonUtil.getValueAsString(data, "version", null);
  }

  /**
   * Gets storage map.
   *
   * @return fields under storage as a map
   */
  public Map<String, String> getStorage() {
    final Map<String, String> map = new HashMap<>();
    JsonUtil.getObjectNode(data, "storage")
        .orElse(JsonUtil.createEmptyObjectNode())
        .fields()
        .forEachRemaining(
            (entry) -> {
              map.put(entry.getKey(), entry.getValue().asText());
            });
    return map;
  }
}
