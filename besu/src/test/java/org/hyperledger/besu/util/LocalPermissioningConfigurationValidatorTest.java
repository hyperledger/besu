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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class LocalPermissioningConfigurationValidatorTest {

  static final String PERMISSIONING_CONFIG_SEPOLIA_BOOTNODES =
      "/permissioning_config_sepolia_bootnodes.toml";
  static final String PERMISSIONING_CONFIG = "/permissioning_config.toml";
  static final String PERMISSIONING_CONFIG_VALID_HOSTNAME =
      "/permissioning_config_valid_hostname.toml";
  static final String PERMISSIONING_CONFIG_UNKNOWN_HOSTNAME =
      "/permissioning_config_unknown_hostname.toml";

  @Test
  public void sepoliaWithNodesAllowlistOptionWhichDoesIncludeRopstenBootnodesMustNotError()
      throws Exception {

    EthNetworkConfig ethNetworkConfig = EthNetworkConfig.getNetworkConfig(NetworkName.SEPOLIA);

    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_SEPOLIA_BOOTNODES);
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

    EthNetworkConfig ethNetworkConfig = EthNetworkConfig.getNetworkConfig(NetworkName.SEPOLIA);

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
      fail("expected exception because sepolia bootnodes are not in node-allowlist");
    } catch (Exception e) {
      assertThat(e.getMessage()).startsWith("Specified node(s) not in nodes-allowlist");
      assertThat(e.getMessage())
          .contains(
              "enode://9246d00bc8fd1742e5ad2428b80fc4dc45d786283e05ef6edbd9002cbc335d40998444732fbe921cb88e1d2c73d1b1de53bae6a2237996e9bfe14f871baf7066@18.168.182.86:30303");
      assertThat(e.getMessage())
          .contains(
              "enode://ec66ddcf1a974950bd4c782789a7e04f8aa7110a72569b6e65fcd51e937e74eed303b1ea734e4d19cfaec9fbff9b6ee65bf31dcb50ba79acce9dd63a6aca61c7@52.14.151.177:30303");
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
        ImmutableEnodeDnsConfiguration.builder().dnsEnabled(true).updateEnabled(false).build();

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
            "Invalid IP address or DNS query resolved an invalid IP. --Xdns-enabled is true but --Xdns-update-enabled flag is false.");
  }
}
