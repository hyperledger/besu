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
package org.hyperledger.besu.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeDnsConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.ImmutableEnodeDnsConfiguration;
import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfigurationBuilder;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LocalPermissioningConfigurationValidatorTest {

  static final String PERMISSIONING_CONFIG_ROPSTEN_BOOTNODES =
      "/permissioning_config_ropsten_bootnodes.toml";
  static final String PERMISSIONING_CONFIG = "/permissioning_config.toml";
  static final String PERMISSIONING_CONFIG_VALID_HOSTNAME =
      "/permissioning_config_valid_hostname.toml";
  static final String PERMISSIONING_CONFIG_UNKNOWN_HOSTNAME =
      "/permissioning_config_unknown_hostname.toml";

  @Test
  public void ropstenWithNodesAllowlistOptionWhichDoesIncludeRopstenBootnodesMustNotError()
      throws Exception {

    EthNetworkConfig ethNetworkConfig = EthNetworkConfig.getNetworkConfig(NetworkName.ROPSTEN);

    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_ROPSTEN_BOOTNODES);
    final Path toml = Files.createTempFile("toml", "");
    Files.write(toml, Resources.toByteArray(configFile));

    LocalPermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(
            true,
            EnodeDnsConfiguration.DEFAULT_CONFIG,
            toml.toAbsolutePath().toString(),
            true,
            toml.toAbsolutePath().toString());

    final List<EnodeURL> enodeURIs = ethNetworkConfig.getBootNodes();
    PermissioningConfigurationValidator.areAllNodesAreInAllowlist(
        enodeURIs, permissioningConfiguration);
  }

  @Test
  public void nodesAllowlistOptionWhichDoesNotIncludeBootnodesMustError() throws Exception {

    EthNetworkConfig ethNetworkConfig = EthNetworkConfig.getNetworkConfig(NetworkName.ROPSTEN);

    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG);
    final Path toml = Files.createTempFile("toml", "");
    toml.toFile().deleteOnExit();
    Files.write(toml, Resources.toByteArray(configFile));

    LocalPermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(
            true,
            EnodeDnsConfiguration.DEFAULT_CONFIG,
            toml.toAbsolutePath().toString(),
            true,
            toml.toAbsolutePath().toString());

    try {
      final List<EnodeURL> enodeURIs = ethNetworkConfig.getBootNodes();
      PermissioningConfigurationValidator.areAllNodesAreInAllowlist(
          enodeURIs, permissioningConfiguration);
      fail("expected exception because ropsten bootnodes are not in node-allowlist");
    } catch (Exception e) {
      assertThat(e.getMessage()).startsWith("Specified node(s) not in nodes-allowlist");
      assertThat(e.getMessage())
          .contains(
              "enode://6332792c4a00e3e4ee0926ed89e0d27ef985424d97b6a45bf0f23e51f0dcb5e66b875777506458aea7af6f9e4ffb69f43f3778ee73c81ed9d34c51c4b16b0b0f@52.232.243.152:30303");
      assertThat(e.getMessage())
          .contains(
              "enode://94c15d1b9e2fe7ce56e458b9a3b672ef11894ddedd0c6f247e0f1d3487f52b66208fb4aeb8179fce6e3a749ea93ed147c37976d67af557508d199d9594c35f09@192.81.208.223:30303");
    }
  }

  @Test
  public void nodeAllowlistCheckShouldIgnoreDiscoveryPortParam() throws Exception {
    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG);
    final Path toml = Files.createTempFile("toml", "");
    toml.toFile().deleteOnExit();
    Files.write(toml, Resources.toByteArray(configFile));

    final LocalPermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(
            true,
            EnodeDnsConfiguration.DEFAULT_CONFIG,
            toml.toAbsolutePath().toString(),
            true,
            toml.toAbsolutePath().toString());

    // This node is defined in the PERMISSIONING_CONFIG file without the discovery port
    final EnodeURL enodeURL =
        EnodeURLImpl.fromString(
            "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.9:4567?discport=30303");

    // In an URI comparison the URLs should not match
    boolean isInAllowlist = permissioningConfiguration.getNodeAllowlist().contains(enodeURL);
    assertThat(isInAllowlist).isFalse();

    // However, for the allowlist validation, we should ignore the discovery port and don't throw an
    // error
    try {
      PermissioningConfigurationValidator.areAllNodesAreInAllowlist(
          Lists.newArrayList(enodeURL), permissioningConfiguration);
    } catch (Exception e) {
      fail(
          "Exception not expected. Validation of nodes in allowlist should ignore the optional discovery port param.");
    }
  }

  @Test
  public void nodeAllowlistCheckShouldWorkWithHostnameIfDnsEnabled() throws Exception {
    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_VALID_HOSTNAME);
    final Path toml = Files.createTempFile("toml", "");
    toml.toFile().deleteOnExit();
    Files.write(toml, Resources.toByteArray(configFile));

    final ImmutableEnodeDnsConfiguration enodeDnsConfiguration =
        ImmutableEnodeDnsConfiguration.builder().dnsEnabled(true).updateEnabled(false).build();
    final LocalPermissioningConfiguration permissioningConfiguration =
        PermissioningConfigurationBuilder.permissioningConfiguration(
            true,
            enodeDnsConfiguration,
            toml.toAbsolutePath().toString(),
            true,
            toml.toAbsolutePath().toString());

    // This node is defined in the PERMISSIONING_CONFIG_DNS file without the discovery port
    final EnodeURL enodeURL =
        EnodeURLImpl.fromString(
            "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@localhost:4567?discport=30303",
            enodeDnsConfiguration);

    // In an URI comparison the URLs should not match
    boolean isInAllowlist = permissioningConfiguration.getNodeAllowlist().contains(enodeURL);
    assertThat(isInAllowlist).isFalse();

    // However, for the allowlist validation, we should ignore the discovery port and don't throw an
    // error
    try {
      PermissioningConfigurationValidator.areAllNodesAreInAllowlist(
          Lists.newArrayList(enodeURL), permissioningConfiguration);
    } catch (Exception e) {
      fail(
          "Exception not expected. Validation of nodes in allowlist should ignore the optional discovery port param.");
    }
  }

  @Test
  public void nodeAllowlistCheckShouldNotWorkWithHostnameWhenDnsDisabled() throws Exception {
    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_VALID_HOSTNAME);
    final Path toml = Files.createTempFile("toml", "");
    toml.toFile().deleteOnExit();
    Files.write(toml, Resources.toByteArray(configFile));

    final ImmutableEnodeDnsConfiguration enodeDnsConfiguration =
        ImmutableEnodeDnsConfiguration.builder().dnsEnabled(false).updateEnabled(false).build();

    assertThatThrownBy(
            () ->
                PermissioningConfigurationBuilder.permissioningConfiguration(
                    true,
                    enodeDnsConfiguration,
                    toml.toAbsolutePath().toString(),
                    true,
                    toml.toAbsolutePath().toString()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Invalid enode URL syntax 'enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@localhost:4567'. "
                + "Enode URL should have the following format 'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'. Invalid ip address.");
  }

  @Test
  public void nodeAllowlistCheckShouldNotWorkWithUnknownHostnameWhenOnlyDnsEnabled()
      throws Exception {
    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_UNKNOWN_HOSTNAME);
    final Path toml = Files.createTempFile("toml", "");
    toml.toFile().deleteOnExit();
    Files.write(toml, Resources.toByteArray(configFile));

    final ImmutableEnodeDnsConfiguration enodeDnsConfiguration =
        ImmutableEnodeDnsConfiguration.builder().dnsEnabled(false).updateEnabled(false).build();

    assertThatThrownBy(
            () ->
                PermissioningConfigurationBuilder.permissioningConfiguration(
                    true,
                    enodeDnsConfiguration,
                    toml.toAbsolutePath().toString(),
                    true,
                    toml.toAbsolutePath().toString()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Invalid enode URL syntax 'enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@hostname:4567'. "
                + "Enode URL should have the following format 'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'. Invalid ip address.");
  }
}
