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
package org.hyperledger.besu.ethereum.permissioning.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.NodePermissioningControllerFactory;
import org.hyperledger.besu.ethereum.permissioning.NodeSmartContractPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.SmartContractPermissioningConfiguration;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NodePermissioningControllerFactoryTest {

  @Mock private Synchronizer synchronizer;
  @Mock private TransactionSimulator transactionSimulator;

  private final String enode =
      "enode://5f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:1111";
  Collection<EnodeURL> bootnodes = Collections.emptyList();
  EnodeURL selfEnode = EnodeURL.fromString(enode);
  LocalPermissioningConfiguration localPermissioningConfig;
  SmartContractPermissioningConfiguration smartContractPermissioningConfiguration;
  PermissioningConfiguration config;

  @Test
  public void testCreateWithNeitherPermissioningEnabled() {
    config = new PermissioningConfiguration(Optional.empty(), Optional.empty());
    NodePermissioningControllerFactory factory = new NodePermissioningControllerFactory();
    NodePermissioningController controller =
        factory.create(
            config,
            synchronizer,
            bootnodes,
            selfEnode.getNodeId(),
            transactionSimulator,
            new NoOpMetricsSystem());

    List<NodePermissioningProvider> providers = controller.getProviders();
    assertThat(providers.size()).isEqualTo(0);
    assertThat(controller.getSyncStatusNodePermissioningProvider()).isNotPresent();
  }

  @Test
  public void testCreateWithSmartContractNodePermissioningEnabledOnly() {
    smartContractPermissioningConfiguration = new SmartContractPermissioningConfiguration();
    smartContractPermissioningConfiguration.setNodeSmartContractAddress(
        Address.fromHexString("0x0000000000000000000000000000000000001234"));
    smartContractPermissioningConfiguration.setSmartContractNodeWhitelistEnabled(true);
    config =
        new PermissioningConfiguration(
            Optional.empty(), Optional.of(smartContractPermissioningConfiguration));

    NodePermissioningControllerFactory factory = new NodePermissioningControllerFactory();
    NodePermissioningController controller =
        factory.create(
            config,
            synchronizer,
            bootnodes,
            selfEnode.getNodeId(),
            transactionSimulator,
            new NoOpMetricsSystem());

    List<NodePermissioningProvider> providers = controller.getProviders();
    assertThat(providers.size()).isEqualTo(1);

    NodePermissioningProvider p1 = providers.get(0);
    assertThat(p1).isInstanceOf(NodeSmartContractPermissioningController.class);
    assertThat(controller.getSyncStatusNodePermissioningProvider()).isEmpty();
  }

  @Test
  public void testCreateWithLocalNodePermissioningEnabledOnly() {
    localPermissioningConfig = LocalPermissioningConfiguration.createDefault();
    localPermissioningConfig.setNodeWhitelist(Collections.emptyList());
    localPermissioningConfig.setNodePermissioningConfigFilePath("fake-file-path");
    config =
        new PermissioningConfiguration(Optional.of(localPermissioningConfig), Optional.empty());

    NodePermissioningControllerFactory factory = new NodePermissioningControllerFactory();
    NodePermissioningController controller =
        factory.create(
            config,
            synchronizer,
            bootnodes,
            selfEnode.getNodeId(),
            transactionSimulator,
            new NoOpMetricsSystem());

    List<NodePermissioningProvider> providers = controller.getProviders();
    assertThat(providers.size()).isEqualTo(1);

    NodePermissioningProvider p1 = providers.get(0);
    assertThat(p1).isInstanceOf(NodeLocalConfigPermissioningController.class);
    assertThat(controller.getSyncStatusNodePermissioningProvider()).isNotPresent();
  }

  @Test
  public void testCreateWithLocalNodeAndSmartContractPermissioningEnabled() {
    localPermissioningConfig = LocalPermissioningConfiguration.createDefault();
    localPermissioningConfig.setNodeWhitelist(Collections.emptyList());
    localPermissioningConfig.setNodePermissioningConfigFilePath("fake-file-path");

    smartContractPermissioningConfiguration = new SmartContractPermissioningConfiguration();
    smartContractPermissioningConfiguration.setNodeSmartContractAddress(
        Address.fromHexString("0x0000000000000000000000000000000000001234"));
    smartContractPermissioningConfiguration.setSmartContractNodeWhitelistEnabled(true);
    config =
        new PermissioningConfiguration(
            Optional.of(localPermissioningConfig),
            Optional.of(smartContractPermissioningConfiguration));

    NodePermissioningControllerFactory factory = new NodePermissioningControllerFactory();
    NodePermissioningController controller =
        factory.create(
            config,
            synchronizer,
            bootnodes,
            selfEnode.getNodeId(),
            transactionSimulator,
            new NoOpMetricsSystem());

    List<NodePermissioningProvider> providers = controller.getProviders();
    assertThat(providers.size()).isEqualTo(2);

    NodePermissioningProvider p1 = providers.get(0);
    NodePermissioningProvider p2 = providers.get(1);
    if (p1.getClass() == NodeLocalConfigPermissioningController.class) {
      assertThat(p2).isInstanceOf(NodeSmartContractPermissioningController.class);
    } else {
      assertThat(p2).isInstanceOf(NodeLocalConfigPermissioningController.class);
      assertThat(p1).isInstanceOf(NodeSmartContractPermissioningController.class);
    }
    assertThat(controller.getSyncStatusNodePermissioningProvider()).isEmpty();
  }

  @Test
  public void testCreateWithSmartContractNodePermissioningEnabledOnlyAndBootnode() {
    final Collection<EnodeURL> fixedNodes = Collections.singleton(selfEnode);

    smartContractPermissioningConfiguration = new SmartContractPermissioningConfiguration();
    smartContractPermissioningConfiguration.setNodeSmartContractAddress(
        Address.fromHexString("0x0000000000000000000000000000000000001234"));
    smartContractPermissioningConfiguration.setSmartContractNodeWhitelistEnabled(true);
    config =
        new PermissioningConfiguration(
            Optional.empty(), Optional.of(smartContractPermissioningConfiguration));

    NodePermissioningControllerFactory factory = new NodePermissioningControllerFactory();
    NodePermissioningController controller =
        factory.create(
            config,
            synchronizer,
            fixedNodes,
            selfEnode.getNodeId(),
            transactionSimulator,
            new NoOpMetricsSystem());

    assertThat(controller.getSyncStatusNodePermissioningProvider()).isPresent();
  }

  @Test
  public void createOnchainShouldFailIfValidationFails() {
    smartContractPermissioningConfiguration = new SmartContractPermissioningConfiguration();
    smartContractPermissioningConfiguration.setNodeSmartContractAddress(
        Address.fromHexString("0x0000000000000000000000000000000000001234"));
    smartContractPermissioningConfiguration.setSmartContractNodeWhitelistEnabled(true);
    config =
        new PermissioningConfiguration(
            Optional.empty(), Optional.of(smartContractPermissioningConfiguration));

    when(transactionSimulator.processAtHead(any())).thenThrow(new RuntimeException());

    final Throwable thrown =
        catchThrowable(
            () ->
                new NodePermissioningControllerFactory()
                    .create(
                        config,
                        synchronizer,
                        bootnodes,
                        selfEnode.getNodeId(),
                        transactionSimulator,
                        new NoOpMetricsSystem()));

    assertThat(thrown)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Error validating onchain node permissioning smart contract configuration");
  }
}
