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
package tech.pegasys.pantheon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;

import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.io.Resources;
import org.junit.Test;

public class PermissioningConfigurationBuilderTest {

  static final String PERMISSIONING_CONFIG_TOML = "permissioning_config.toml";
  static final String PERMISSIONING_CONFIG_ACCOUNT_WHITELIST_ONLY =
      "permissioning_config_account_whitelist_only.toml";
  static final String PERMISSIONING_CONFIG_NODE_WHITELIST_ONLY =
      "permissioning_config_node_whitelist_only.toml";
  static final String PERMISSIONING_CONFIG_INVALID_ENODE =
      "permissioning_config_invalid_enode.toml";
  static final String PERMISSIONING_CONFIG_EMPTY_WHITELISTS =
      "permissioning_config_empty_whitelists.toml";
  static final String PERMISSIONING_CONFIG_ABSENT_WHITELISTS =
      "permissioning_config_absent_whitelists.toml";

  private final String VALID_NODE_ID =
      "6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0";

  @Test
  public void permissioningConfig() throws IOException {

    final String uri = "enode://" + VALID_NODE_ID + "@192.168.0.9:4567";
    final String uri2 = "enode://" + VALID_NODE_ID + "@192.169.0.9:4568";

    final URL configFile = Resources.getResource(PERMISSIONING_CONFIG_TOML);
    final Path toml = Files.createTempFile("toml", "");
    Files.write(toml, Resources.toByteArray(configFile));

    PermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(
            toml.toAbsolutePath().toString(), true, true);

    assertThat(permissioningConfiguration.isAccountWhitelistSet()).isTrue();
    assertThat(permissioningConfiguration.getAccountWhitelist())
        .containsExactly("0x0000000000000000000000000000000000000009");
    assertThat(permissioningConfiguration.isNodeWhitelistSet()).isTrue();
    assertThat(permissioningConfiguration.getNodeWhitelist())
        .containsExactly(URI.create(uri), URI.create(uri2));
  }

  @Test
  public void permissioningConfigWithOnlyNodeWhitelistSet() throws IOException {

    final String uri = "enode://" + VALID_NODE_ID + "@192.168.0.9:4567";

    final URL configFile = Resources.getResource(PERMISSIONING_CONFIG_NODE_WHITELIST_ONLY);
    final Path toml = Files.createTempFile("toml", "");
    Files.write(toml, Resources.toByteArray(configFile));

    PermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(
            toml.toAbsolutePath().toString(), true, false);

    assertThat(permissioningConfiguration.isAccountWhitelistSet()).isFalse();
    assertThat(permissioningConfiguration.isNodeWhitelistSet()).isTrue();
    assertThat(permissioningConfiguration.getNodeWhitelist()).containsExactly(URI.create(uri));
  }

  @Test
  public void permissioningConfigWithOnlyAccountWhitelistSet() throws IOException {

    final URL configFile = Resources.getResource(PERMISSIONING_CONFIG_ACCOUNT_WHITELIST_ONLY);
    final Path toml = Files.createTempFile("toml", "");
    Files.write(toml, Resources.toByteArray(configFile));

    PermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(
            toml.toAbsolutePath().toString(), false, true);

    assertThat(permissioningConfiguration.isNodeWhitelistSet()).isFalse();
    assertThat(permissioningConfiguration.isAccountWhitelistSet()).isTrue();
    assertThat(permissioningConfiguration.getAccountWhitelist())
        .containsExactly("0x0000000000000000000000000000000000000009");
  }

  @Test
  public void permissioningConfigWithInvalidEnode() throws IOException {

    final URL configFile = Resources.getResource(PERMISSIONING_CONFIG_INVALID_ENODE);
    final Path toml = Files.createTempFile("toml", "");
    Files.write(toml, Resources.toByteArray(configFile));

    try {
      PermissioningConfigurationBuilder.permissioningConfiguration(
          toml.toAbsolutePath().toString(), true, true);
      fail("Expecting IllegalArgumentException: Enode URL contains an invalid node ID");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).startsWith("Enode URL contains an invalid node ID");
    }
  }

  @Test
  public void permissioningConfigWithEmptyWhitelistMustNotError() throws IOException {

    final URL configFile = Resources.getResource(PERMISSIONING_CONFIG_EMPTY_WHITELISTS);
    final Path toml = Files.createTempFile("toml", "");
    Files.write(toml, Resources.toByteArray(configFile));

    PermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(
            toml.toAbsolutePath().toString(), true, true);

    assertThat(permissioningConfiguration.isNodeWhitelistSet()).isTrue();
    assertThat(permissioningConfiguration.getNodeWhitelist()).isEmpty();
    assertThat(permissioningConfiguration.isAccountWhitelistSet()).isTrue();
    assertThat(permissioningConfiguration.getAccountWhitelist()).isEmpty();
  }

  @Test
  public void permissioningConfigWithAbsentWhitelistMustSetValues() throws IOException {

    final URL configFile = Resources.getResource(PERMISSIONING_CONFIG_ABSENT_WHITELISTS);
    final Path toml = Files.createTempFile("toml", "");
    Files.write(toml, Resources.toByteArray(configFile));

    PermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(
            toml.toAbsolutePath().toString(), true, true);

    assertThat(permissioningConfiguration.isNodeWhitelistSet()).isTrue();
    assertThat(permissioningConfiguration.getNodeWhitelist()).isEmpty();
    assertThat(permissioningConfiguration.isAccountWhitelistSet()).isTrue();
    assertThat(permissioningConfiguration.getAccountWhitelist()).isEmpty();
  }

  @Test
  public void permissioningConfigFromFileMustSetFilePath() throws IOException {

    final URL configFile = Resources.getResource(PERMISSIONING_CONFIG_ABSENT_WHITELISTS);
    final Path toml = Files.createTempFile("toml", "");
    Files.write(toml, Resources.toByteArray(configFile));

    PermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfigurationFromToml(
            toml.toString(), true, true);

    assertThat(permissioningConfiguration.getConfigurationFilePath()).isEqualTo(toml.toString());

    assertThat(permissioningConfiguration.isNodeWhitelistSet()).isTrue();
    assertThat(permissioningConfiguration.getNodeWhitelist()).isEmpty();
    assertThat(permissioningConfiguration.isAccountWhitelistSet()).isTrue();
    assertThat(permissioningConfiguration.getAccountWhitelist()).isEmpty();
  }
}
