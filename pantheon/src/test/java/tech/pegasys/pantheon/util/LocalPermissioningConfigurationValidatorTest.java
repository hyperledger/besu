/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import tech.pegasys.pantheon.cli.EthNetworkConfig;
import tech.pegasys.pantheon.cli.NetworkName;
import tech.pegasys.pantheon.ethereum.permissioning.LocalPermissioningConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfigurationBuilder;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.io.Resources;
import org.junit.Test;

public class LocalPermissioningConfigurationValidatorTest {

  static final String PERMISSIONING_CONFIG_ROPSTEN_BOOTNODES =
      "/permissioning_config_ropsten_bootnodes.toml";
  static final String PERMISSIONING_CONFIG = "/permissioning_config.toml";

  @Test
  public void ropstenWithNodesWhitelistOptionWhichDoesIncludeRopstenBootnodesMustNotError()
      throws Exception {

    EthNetworkConfig ethNetworkConfig = EthNetworkConfig.getNetworkConfig(NetworkName.ROPSTEN);

    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_ROPSTEN_BOOTNODES);
    final Path toml = Files.createTempFile("toml", "");
    Files.write(toml, Resources.toByteArray(configFile));

    LocalPermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(
            true, toml.toAbsolutePath().toString(), true, toml.toAbsolutePath().toString());

    PermissioningConfigurationValidator.areAllNodesAreInWhitelist(
        ethNetworkConfig.getBootNodes(), permissioningConfiguration);
  }

  @Test
  public void nodesWhitelistOptionWhichDoesNotIncludeBootnodesMustError() throws Exception {

    EthNetworkConfig ethNetworkConfig = EthNetworkConfig.getNetworkConfig(NetworkName.ROPSTEN);

    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG);
    final Path toml = Files.createTempFile("toml", "");
    toml.toFile().deleteOnExit();
    Files.write(toml, Resources.toByteArray(configFile));

    LocalPermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(
            true, toml.toAbsolutePath().toString(), true, toml.toAbsolutePath().toString());

    try {
      PermissioningConfigurationValidator.areAllNodesAreInWhitelist(
          ethNetworkConfig.getBootNodes(), permissioningConfiguration);
      fail("expected exception because ropsten bootnodes are not in node-whitelist");
    } catch (Exception e) {
      assertThat(e.getMessage()).startsWith("Specified node(s) not in nodes-whitelist");
      assertThat(e.getMessage())
          .contains(
              "enode://6332792c4a00e3e4ee0926ed89e0d27ef985424d97b6a45bf0f23e51f0dcb5e66b875777506458aea7af6f9e4ffb69f43f3778ee73c81ed9d34c51c4b16b0b0f@52.232.243.152:30303");
      assertThat(e.getMessage())
          .contains(
              "enode://94c15d1b9e2fe7ce56e458b9a3b672ef11894ddedd0c6f247e0f1d3487f52b66208fb4aeb8179fce6e3a749ea93ed147c37976d67af557508d199d9594c35f09@192.81.208.223:30303");
    }
  }
}
