/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.config;

import java.util.Map;

import io.vertx.core.json.JsonObject;

public class GenesisAllocation {
  private final String address;
  private final JsonObject data;

  GenesisAllocation(final String address, final JsonObject data) {
    this.address = address;
    this.data = data;
  }

  public String getAddress() {
    return address;
  }

  public String getBalance() {
    return data.getString("balance", "0");
  }

  public String getCode() {
    return data.getString("code");
  }

  public String getNonce() {
    return data.getString("nonce", "0");
  }

  public String getVersion() {
    return data.getString("version");
  }

  public Map<String, Object> getStorage() {
    return data.getJsonObject("storage", new JsonObject()).getMap();
  }
}
