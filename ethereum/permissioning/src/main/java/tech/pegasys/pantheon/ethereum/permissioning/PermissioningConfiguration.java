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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PermissioningConfiguration {
  private List<URI> nodeWhitelist;
  private List<String> accountWhitelist;
  private boolean nodeWhitelistEnabled;
  private boolean accountWhitelistEnabled;
  private String configurationFilePath;

  public List<URI> getNodeWhitelist() {
    return nodeWhitelist;
  }

  public static PermissioningConfiguration createDefault() {
    final PermissioningConfiguration config = new PermissioningConfiguration();
    config.nodeWhitelist = new ArrayList<>();
    config.accountWhitelist = new ArrayList<>();
    return config;
  }

  public void setNodeWhitelist(final Collection<URI> nodeWhitelist) {
    if (nodeWhitelist != null) {
      this.nodeWhitelist.addAll(nodeWhitelist);
      this.nodeWhitelistEnabled = true;
    }
  }

  public boolean isNodeWhitelistEnabled() {
    return nodeWhitelistEnabled;
  }

  public List<String> getAccountWhitelist() {
    return accountWhitelist;
  }

  public void setAccountWhitelist(final Collection<String> accountWhitelist) {
    if (accountWhitelist != null) {
      this.accountWhitelist.addAll(accountWhitelist);
      this.accountWhitelistEnabled = true;
    }
  }

  public boolean isAccountWhitelistEnabled() {
    return accountWhitelistEnabled;
  }

  public String getConfigurationFilePath() {
    return configurationFilePath;
  }

  public void setConfigurationFilePath(final String configurationFilePath) {
    this.configurationFilePath = configurationFilePath;
  }
}
