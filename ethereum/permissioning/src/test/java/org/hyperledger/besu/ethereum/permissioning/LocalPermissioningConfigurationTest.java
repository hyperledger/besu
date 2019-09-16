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
package org.hyperledger.besu.ethereum.permissioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Arrays;

import org.junit.Test;

public class LocalPermissioningConfigurationTest {

  final URI[] nodes = {
    URI.create("enode://001@123:4567"),
    URI.create("enode://002@123:4567"),
    URI.create("enode://003@123:4567")
  };

  @Test
  public void defaultConfiguration() {
    final LocalPermissioningConfiguration configuration =
        LocalPermissioningConfiguration.createDefault();
    assertThat(configuration.getNodeWhitelist()).isEmpty();
    assertThat(configuration.isNodeWhitelistEnabled()).isFalse();
    assertThat(configuration.getAccountWhitelist()).isEmpty();
    assertThat(configuration.isAccountWhitelistEnabled()).isFalse();
  }

  @Test
  public void setNodeWhitelist() {
    final LocalPermissioningConfiguration configuration =
        LocalPermissioningConfiguration.createDefault();
    configuration.setNodeWhitelist(Arrays.asList(nodes));
    assertThat(configuration.getNodeWhitelist()).containsExactlyInAnyOrder(nodes);
    assertThat(configuration.isNodeWhitelistEnabled()).isTrue();
  }

  @Test
  public void setNodeWhiteListPassingNull() {
    final LocalPermissioningConfiguration configuration =
        LocalPermissioningConfiguration.createDefault();
    configuration.setNodeWhitelist(null);
    assertThat(configuration.getNodeWhitelist()).isEmpty();
    assertThat(configuration.isNodeWhitelistEnabled()).isFalse();
  }

  @Test
  public void setAccountWhitelist() {
    final String[] accounts = {"1111111111111111", "2222222222222222", "ffffffffffffffff"};
    final LocalPermissioningConfiguration configuration =
        LocalPermissioningConfiguration.createDefault();
    configuration.setAccountWhitelist(Arrays.asList(accounts));
    assertThat(configuration.getAccountWhitelist()).containsExactlyInAnyOrder(accounts);
    assertThat(configuration.isAccountWhitelistEnabled()).isTrue();
  }

  @Test
  public void setAccountWhiteListPassingNull() {
    final LocalPermissioningConfiguration configuration =
        LocalPermissioningConfiguration.createDefault();
    configuration.setAccountWhitelist(null);
    assertThat(configuration.getAccountWhitelist()).isEmpty();
    assertThat(configuration.isAccountWhitelistEnabled()).isFalse();
  }
}
