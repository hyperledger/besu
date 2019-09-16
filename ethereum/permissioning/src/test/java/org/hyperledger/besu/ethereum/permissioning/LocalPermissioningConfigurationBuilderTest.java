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
package org.hyperledger.besu.ethereum.permissioning;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.io.Resources;
import org.junit.Test;

public class LocalPermissioningConfigurationBuilderTest {

  private static final String PERMISSIONING_CONFIG_VALID = "/permissioning_config.toml";
  private static final String PERMISSIONING_CONFIG_ACCOUNT_WHITELIST_ONLY =
      "/permissioning_config_account_whitelist_only.toml";
  private static final String PERMISSIONING_CONFIG_NODE_WHITELIST_ONLY =
      "/permissioning_config_node_whitelist_only.toml";
  private static final String PERMISSIONING_CONFIG_INVALID_ENODE =
      "/permissioning_config_invalid_enode.toml";
  private static final String PERMISSIONING_CONFIG_INVALID_ACCOUNT =
      "/permissioning_config_invalid_account.toml";
  private static final String PERMISSIONING_CONFIG_EMPTY_WHITELISTS =
      "/permissioning_config_empty_whitelists.toml";
  private static final String PERMISSIONING_CONFIG_ABSENT_WHITELISTS =
      "/permissioning_config_absent_whitelists.toml";
  private static final String PERMISSIONING_CONFIG_UNRECOGNIZED_KEY =
      "/permissioning_config_unrecognized_key.toml";
  private static final String PERMISSIONING_CONFIG_NODE_WHITELIST_ONLY_MULTILINE =
      "/permissioning_config_node_whitelist_only_multiline.toml";

  private final String VALID_NODE_ID =
      "6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0";

  @Test
  public void permissioningConfig() throws Exception {
    final String uri = "enode://" + VALID_NODE_ID + "@192.168.0.9:4567";
    final String uri2 = "enode://" + VALID_NODE_ID + "@192.169.0.9:4568";

    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_VALID);
    final Path toml = createTempFile("toml", Resources.toByteArray(configFile));

    LocalPermissioningConfiguration permissioningConfiguration = permissioningConfig(toml);

    assertThat(permissioningConfiguration.isAccountWhitelistEnabled()).isTrue();
    assertThat(permissioningConfiguration.getAccountWhitelist())
        .containsExactly("0x0000000000000000000000000000000000000009");
    assertThat(permissioningConfiguration.isNodeWhitelistEnabled()).isTrue();
    assertThat(permissioningConfiguration.getNodeWhitelist())
        .containsExactly(URI.create(uri), URI.create(uri2));
  }

  @Test
  public void permissioningConfigWithOnlyNodeWhitelistSet() throws Exception {
    final String uri = "enode://" + VALID_NODE_ID + "@192.168.0.9:4567";

    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_NODE_WHITELIST_ONLY);
    final Path toml = createTempFile("toml", Resources.toByteArray(configFile));

    LocalPermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(
            true, toml.toAbsolutePath().toString(), false, toml.toAbsolutePath().toString());

    assertThat(permissioningConfiguration.isAccountWhitelistEnabled()).isFalse();
    assertThat(permissioningConfiguration.isNodeWhitelistEnabled()).isTrue();
    assertThat(permissioningConfiguration.getNodeWhitelist()).containsExactly(URI.create(uri));
  }

  @Test
  public void permissioningConfigWithOnlyAccountWhitelistSet() throws Exception {
    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_ACCOUNT_WHITELIST_ONLY);
    final Path toml = createTempFile("toml", Resources.toByteArray(configFile));

    LocalPermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(
            false, toml.toAbsolutePath().toString(), true, toml.toAbsolutePath().toString());

    assertThat(permissioningConfiguration.isNodeWhitelistEnabled()).isFalse();
    assertThat(permissioningConfiguration.isAccountWhitelistEnabled()).isTrue();
    assertThat(permissioningConfiguration.getAccountWhitelist())
        .containsExactly("0x0000000000000000000000000000000000000009");
  }

  @Test
  public void permissioningConfigWithInvalidAccount() throws Exception {
    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_INVALID_ACCOUNT);
    final Path toml = createTempFile("toml", Resources.toByteArray(configFile));

    final Throwable thrown = catchThrowable(() -> accountOnlyPermissioningConfig(toml));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Invalid account 0xfoo");
  }

  @Test
  public void permissioningConfigWithInvalidEnode() throws Exception {
    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_INVALID_ENODE);
    final Path toml = createTempFile("toml", Resources.toByteArray(configFile));

    final Throwable thrown = catchThrowable(() -> nodeOnlyPermissioningConfig(toml));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid node ID");
  }

  @Test
  public void permissioningConfigWithEmptyWhitelistMustNotError() throws Exception {
    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_EMPTY_WHITELISTS);
    final Path toml = createTempFile("toml", Resources.toByteArray(configFile));

    LocalPermissioningConfiguration permissioningConfiguration = permissioningConfig(toml);

    assertThat(permissioningConfiguration.isNodeWhitelistEnabled()).isTrue();
    assertThat(permissioningConfiguration.getNodeWhitelist()).isEmpty();
    assertThat(permissioningConfiguration.isAccountWhitelistEnabled()).isTrue();
    assertThat(permissioningConfiguration.getAccountWhitelist()).isEmpty();
  }

  @Test
  public void permissioningConfigWithAbsentWhitelistMustThrowException() throws Exception {
    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_ABSENT_WHITELISTS);
    final Path toml = createTempFile("toml", Resources.toByteArray(configFile));

    final Throwable thrown = catchThrowable(() -> permissioningConfig(toml));

    assertThat(thrown).isInstanceOf(Exception.class).hasMessageContaining("Unexpected end of line");
  }

  @Test
  public void permissioningConfigWithUnrecognizedKeyMustThrowException() throws Exception {
    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_UNRECOGNIZED_KEY);
    final Path toml = createTempFile("toml", Resources.toByteArray(configFile));

    final Throwable thrown = catchThrowable(() -> accountOnlyPermissioningConfig(toml));

    assertThat(thrown)
        .isInstanceOf(Exception.class)
        .hasMessageContaining("config option missing")
        .hasMessageContaining(PermissioningConfigurationBuilder.ACCOUNTS_WHITELIST_KEY);
  }

  @Test
  public void permissioningConfigWithEmptyFileMustThrowException() throws Exception {
    // write an empty file
    final Path toml = createTempFile("toml", "".getBytes(UTF_8));

    final Throwable thrown = catchThrowable(() -> permissioningConfig(toml));

    assertThat(thrown).isInstanceOf(Exception.class).hasMessageContaining("Empty TOML result");
  }

  @Test
  public void permissioningConfigFromFileMustSetFilePath() throws Exception {
    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_VALID);
    final Path toml = createTempFile("toml", Resources.toByteArray(configFile));

    LocalPermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(
            true, toml.toString(), true, toml.toString());

    assertThat(permissioningConfiguration.getNodePermissioningConfigFilePath())
        .isEqualTo(toml.toString());
    assertThat(permissioningConfiguration.getAccountPermissioningConfigFilePath())
        .isEqualTo(toml.toString());
  }

  @Test
  public void permissioningConfigFromNonexistentFileMustThrowException() {
    final Throwable thrown =
        catchThrowable(() -> permissioningConfig(Paths.get("file-does-not-exist")));

    assertThat(thrown)
        .isInstanceOf(Exception.class)
        .hasMessageContaining("Configuration file does not exist");
  }

  @Test
  public void permissioningConfigFromMultilineFileMustParseCorrectly() throws Exception {
    final URL configFile =
        this.getClass().getResource(PERMISSIONING_CONFIG_NODE_WHITELIST_ONLY_MULTILINE);
    final LocalPermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(
            true, configFile.getPath(), false, configFile.getPath());

    assertThat(permissioningConfiguration.isNodeWhitelistEnabled()).isTrue();
    assertThat(permissioningConfiguration.getNodeWhitelist().size()).isEqualTo(5);
  }

  private LocalPermissioningConfiguration accountOnlyPermissioningConfig(final Path toml)
      throws Exception {
    return PermissioningConfigurationBuilder.permissioningConfiguration(
        false, null, true, toml.toAbsolutePath().toString());
  }

  private LocalPermissioningConfiguration nodeOnlyPermissioningConfig(final Path toml)
      throws Exception {
    return PermissioningConfigurationBuilder.permissioningConfiguration(
        true, toml.toAbsolutePath().toString(), false, null);
  }

  private LocalPermissioningConfiguration permissioningConfig(final Path toml) throws Exception {
    return PermissioningConfigurationBuilder.permissioningConfiguration(
        true, toml.toAbsolutePath().toString(), true, toml.toAbsolutePath().toString());
  }

  private Path createTempFile(final String filename, final byte[] contents) throws IOException {
    final Path file = Files.createTempFile(filename, "");
    Files.write(file, contents);
    file.toFile().deleteOnExit();
    return file;
  }
}
