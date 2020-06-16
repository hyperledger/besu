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
    assertThat(configuration.getNodeAllowlist()).isEmpty();
    assertThat(configuration.isNodeAllowlistEnabled()).isFalse();
    assertThat(configuration.getAccountAllowlist()).isEmpty();
    assertThat(configuration.isAccountAllowlistEnabled()).isFalse();
  }

  @Test
  public void setNodeWhitelist() {
    final LocalPermissioningConfiguration configuration =
        LocalPermissioningConfiguration.createDefault();
    configuration.setNodeAllowlist(Arrays.asList(nodes));
    assertThat(configuration.getNodeAllowlist()).containsExactlyInAnyOrder(nodes);
    assertThat(configuration.isNodeAllowlistEnabled()).isTrue();
  }

  @Test
  public void setNodeWhiteListPassingNull() {
    final LocalPermissioningConfiguration configuration =
        LocalPermissioningConfiguration.createDefault();
    configuration.setNodeAllowlist(null);
    assertThat(configuration.getNodeAllowlist()).isEmpty();
    assertThat(configuration.isNodeAllowlistEnabled()).isFalse();
  }

  @Test
  public void setAccountWhitelist() {
    final String[] accounts = {"1111111111111111", "2222222222222222", "ffffffffffffffff"};
    final LocalPermissioningConfiguration configuration =
        LocalPermissioningConfiguration.createDefault();
    configuration.setAccountAllowlist(Arrays.asList(accounts));
    assertThat(configuration.getAccountAllowlist()).containsExactlyInAnyOrder(accounts);
    assertThat(configuration.isAccountAllowlistEnabled()).isTrue();
  }

  @Test
  public void setAccountWhiteListPassingNull() {
    final LocalPermissioningConfiguration configuration =
        LocalPermissioningConfiguration.createDefault();
    configuration.setAccountAllowlist(null);
    assertThat(configuration.getAccountAllowlist()).isEmpty();
    assertThat(configuration.isAccountAllowlistEnabled()).isFalse();
  }
}
