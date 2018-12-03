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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.Test;

public class PermissioningConfigurationTest {

  @Test
  public void defaultConfiguration() {
    final PermissioningConfiguration configuration = PermissioningConfiguration.createDefault();
    assertThat(configuration.getNodeWhitelist()).isEmpty();
    assertThat(configuration.isNodeWhitelistSet()).isFalse();
  }

  @Test
  public void setNodeWhitelist() {
    final String[] nodes = {"enode://001@123:4567", "enode://002@123:4567", "enode://003@123:4567"};
    final PermissioningConfiguration configuration = PermissioningConfiguration.createDefault();
    configuration.setNodeWhitelist(Arrays.asList(nodes));
    assertThat(configuration.getNodeWhitelist()).containsExactlyInAnyOrder(nodes);
    assertThat(configuration.isNodeWhitelistSet()).isTrue();
  }

  @Test
  public void setNodeWhiteListPassingNull() {
    final PermissioningConfiguration configuration = PermissioningConfiguration.createDefault();
    configuration.setNodeWhitelist(null);
    assertThat(configuration.getNodeWhitelist()).isEmpty();
    assertThat(configuration.isNodeWhitelistSet()).isFalse();
  }
}
