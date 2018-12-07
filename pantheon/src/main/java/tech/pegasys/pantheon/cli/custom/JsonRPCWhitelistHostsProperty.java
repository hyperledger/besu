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
package tech.pegasys.pantheon.cli.custom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import picocli.CommandLine;

public class JsonRPCWhitelistHostsProperty {
  private List<String> hostnamesWhitelist = Collections.emptyList();

  private JsonRPCWhitelistHostsProperty(final List<String> hostnamesWhitelist) {
    this.hostnamesWhitelist = hostnamesWhitelist;
  }

  public JsonRPCWhitelistHostsProperty() {}

  public List<String> hostnamesWhitelist() {
    return hostnamesWhitelist;
  }

  public static class JsonRPCWhitelistHostsConverter
      implements CommandLine.ITypeConverter<JsonRPCWhitelistHostsProperty> {

    @Override
    public JsonRPCWhitelistHostsProperty convert(final String value) {
      final List<String> hostNames;
      if (value != null && !value.isEmpty()) {
        hostNames = new ArrayList<>(Arrays.asList(value.split("\\s*,\\s*")));
      } else {
        throw new IllegalArgumentException("Property can't be null/empty string");
      }

      if (hostNames.contains("all")) {
        if (hostNames.size() > 1) {
          throw new IllegalArgumentException("Value 'all' can't be used with other hostnames");
        } else {
          return new JsonRPCWhitelistHostsProperty(Collections.singletonList("*"));
        }
      }

      if (hostNames.contains("*")) {
        if (hostNames.size() > 1) {
          throw new IllegalArgumentException("Value '*' can't be used with other hostnames");
        } else {
          return new JsonRPCWhitelistHostsProperty(Collections.singletonList("*"));
        }
      }

      if (hostNames.size() > 0) {
        return new JsonRPCWhitelistHostsProperty(hostNames);
      } else {
        return new JsonRPCWhitelistHostsProperty();
      }
    }
  }
}
