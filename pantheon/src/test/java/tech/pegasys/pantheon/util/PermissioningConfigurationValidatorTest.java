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

import tech.pegasys.pantheon.PermissioningConfigurationBuilder;
import tech.pegasys.pantheon.cli.EthNetworkConfig;
import tech.pegasys.pantheon.cli.NetworkName;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.io.Resources;
import net.consensys.cava.toml.Toml;
import net.consensys.cava.toml.TomlParseResult;
import org.junit.Test;

public class PermissioningConfigurationValidatorTest {

  static final String PERMISSIONING_CONFIG_ROPSTEN_BOOTNODES =
      "permissioning_config_ropsten_bootnodes.toml";
  static final String PERMISSIONING_CONFIG = "permissioning_config.toml";

  @Test
  public void ropstenWithNodesWhitelistOptionWhichDoesIncludeRopstenBootnodesMustNotError()
      throws Exception {

    EthNetworkConfig ethNetworkConfig = EthNetworkConfig.getNetworkConfig(NetworkName.ROPSTEN);

    final URL configFile = Resources.getResource(PERMISSIONING_CONFIG_ROPSTEN_BOOTNODES);
    final Path toml = Files.createTempFile("toml", "");
    Files.write(toml, Resources.toByteArray(configFile));
    final TomlParseResult tomlResult = Toml.parse(toml);

    PermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(tomlResult, true, true);

    PermissioningConfigurationValidator.areAllBootnodesAreInWhitelist(
        ethNetworkConfig, permissioningConfiguration);
  }

  @Test
  public void nodesWhitelistOptionWhichDoesNotIncludeBootnodesMustError() throws Exception {

    EthNetworkConfig ethNetworkConfig = EthNetworkConfig.getNetworkConfig(NetworkName.ROPSTEN);

    final URL configFile = Resources.getResource(PERMISSIONING_CONFIG);
    final Path toml = Files.createTempFile("toml", "");
    Files.write(toml, Resources.toByteArray(configFile));
    final TomlParseResult tomlResult = Toml.parse(toml);

    PermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(tomlResult, true, true);

    try {
      PermissioningConfigurationValidator.areAllBootnodesAreInWhitelist(
          ethNetworkConfig, permissioningConfiguration);
      fail("expected exception because ropsten bootnodes are not in node-whitelist");
    } catch (Exception e) {
      assertThat(e.getMessage().startsWith("Bootnode")).isTrue();
    }
  }
}
