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
package tech.pegasys.pantheon.ethereum.permissioning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PermissioningConfiguration {
  private List<String> nodeWhitelist;
  private boolean nodeWhitelistSet;

  public List<String> getNodeWhitelist() {
    return nodeWhitelist;
  }

  public static PermissioningConfiguration createDefault() {
    final PermissioningConfiguration config = new PermissioningConfiguration();
    config.nodeWhitelist = new ArrayList<>();
    return config;
  }

  public void setNodeWhitelist(final Collection<String> nodeWhitelist) {
    if (nodeWhitelist != null) {
      this.nodeWhitelist.addAll(nodeWhitelist);
      this.nodeWhitelistSet = true;
    }
  }

  public boolean isNodeWhitelistSet() {
    return nodeWhitelistSet;
  }
}
