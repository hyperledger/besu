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
package tech.pegasys.pantheon.ethereum.permissioning;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.config.JsonUtil;
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.peers.EnodeURL;
import tech.pegasys.pantheon.ethereum.transaction.TransactionSimulator;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.plugin.services.metrics.Counter;

import java.io.IOException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NodeSmartContractPermissioningControllerTest {
  @Mock private MetricsSystem metricsSystem;
  @Mock private Counter checkCounter;
  @Mock private Counter checkPermittedCounter;
  @Mock private Counter checkUnpermittedCounter;

  private NodeSmartContractPermissioningController setupController(
      final String resourceName, final String contractAddressString) throws IOException {
    final ProtocolSchedule<Void> protocolSchedule = MainnetProtocolSchedule.create();

    final String emptyContractFile =
        Resources.toString(this.getClass().getResource(resourceName), UTF_8);

    final ObjectNode jsonData = JsonUtil.objectNodeFromString(emptyContractFile, true);
    final GenesisState genesisState =
        GenesisState.fromConfig(GenesisConfigFile.fromConfig(jsonData), protocolSchedule);

    final MutableBlockchain blockchain = createInMemoryBlockchain(genesisState.getBlock());
    final WorldStateArchive worldArchive = createInMemoryWorldStateArchive();

    genesisState.writeStateTo(worldArchive.getMutable());

    final TransactionSimulator ts =
        new TransactionSimulator(blockchain, worldArchive, protocolSchedule);
    final Address contractAddress = Address.fromHexString(contractAddressString);

    when(metricsSystem.createCounter(
            PantheonMetricCategory.PERMISSIONING,
            "node_smart_contract_check_count",
            "Number of times the node smart contract permissioning provider has been checked"))
        .thenReturn(checkCounter);

    when(metricsSystem.createCounter(
            PantheonMetricCategory.PERMISSIONING,
            "node_smart_contract_check_count_permitted",
            "Number of times the node smart contract permissioning provider has been checked and returned permitted"))
        .thenReturn(checkPermittedCounter);

    when(metricsSystem.createCounter(
            PantheonMetricCategory.PERMISSIONING,
            "node_smart_contract_check_count_unpermitted",
            "Number of times the node smart contract permissioning provider has been checked and returned unpermitted"))
        .thenReturn(checkUnpermittedCounter);

    return new NodeSmartContractPermissioningController(contractAddress, ts, metricsSystem);
  }

  private void verifyCountersUntouched() {
    verify(checkCounter, times(0)).inc();
    verify(checkPermittedCounter, times(0)).inc();
    verify(checkUnpermittedCounter, times(0)).inc();
  }

  private void verifyCountersPermitted() {
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(1)).inc();
    verify(checkUnpermittedCounter, times(0)).inc();
  }

  private void verifyCountersUnpermitted() {
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(0)).inc();
    verify(checkUnpermittedCounter, times(1)).inc();
  }

  private void verifyCountersFailedCheck() {
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(0)).inc();
    verify(checkUnpermittedCounter, times(0)).inc();
  }

  @Test
  public void testIpv4Included() throws IOException {
    final NodeSmartContractPermissioningController controller =
        setupController(
            "/NodeSmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    verifyCountersUntouched();

    assertThat(
            controller.isPermitted(
                EnodeURL.fromString(
                    "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:30303"),
                EnodeURL.fromString(
                    "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:30304")))
        .isTrue();

    verifyCountersPermitted();
  }

  @Test
  public void testIpv4DestinationMissing() throws IOException {
    final NodeSmartContractPermissioningController controller =
        setupController(
            "/NodeSmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    verifyCountersUntouched();

    assertThat(
            controller.isPermitted(
                EnodeURL.fromString(
                    "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:30303"),
                EnodeURL.fromString(
                    "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:30305")))
        .isFalse();

    verifyCountersUnpermitted();
  }

  @Test
  public void testIpv4SourceMissing() throws IOException {
    final NodeSmartContractPermissioningController controller =
        setupController(
            "/NodeSmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    verifyCountersUntouched();

    assertThat(
            controller.isPermitted(
                EnodeURL.fromString(
                    "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:30302"),
                EnodeURL.fromString(
                    "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:30304")))
        .isFalse();

    verifyCountersUnpermitted();
  }

  @Test
  public void testIpv6Included() throws IOException {
    final NodeSmartContractPermissioningController controller =
        setupController(
            "/NodeSmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    verifyCountersUntouched();

    assertThat(
            controller.isPermitted(
                EnodeURL.fromString(
                    "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab61@[1:2:3:4:5:6:7:8]:30303"),
                EnodeURL.fromString(
                    "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab62@[1:2:3:4:5:6:7:8]:30304")))
        .isTrue();

    verifyCountersPermitted();
  }

  @Test
  public void testIpv6SourceMissing() throws IOException {
    final NodeSmartContractPermissioningController controller =
        setupController(
            "/NodeSmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    verifyCountersUntouched();

    assertThat(
            controller.isPermitted(
                EnodeURL.fromString(
                    "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab63@[1:2:3:4:5:6:7:8]:30303"),
                EnodeURL.fromString(
                    "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab62@[1:2:3:4:5:6:7:8]:30304")))
        .isFalse();

    verifyCountersUnpermitted();
  }

  @Test
  public void testIpv6DestinationMissing() throws IOException {
    final NodeSmartContractPermissioningController controller =
        setupController(
            "/NodeSmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    verifyCountersUntouched();

    assertThat(
            controller.isPermitted(
                EnodeURL.fromString(
                    "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab61@[1:2:3:4:5:6:7:8]:30303"),
                EnodeURL.fromString(
                    "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab63@[1:2:3:4:5:6:7:8]:30304")))
        .isFalse();

    verifyCountersUnpermitted();
  }

  @Test
  public void testPermissioningContractMissing() throws IOException {
    final NodeSmartContractPermissioningController controller =
        setupController(
            "/NodeSmartContractPermissioningControllerTest/noSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    verifyCountersUntouched();

    assertThatThrownBy(
            () ->
                controller.isPermitted(
                    EnodeURL.fromString(
                        "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab61@[1:2:3:4:5:6:7:8]:30303"),
                    EnodeURL.fromString(
                        "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab63@[1:2:3:4:5:6:7:8]:30304")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Permissioning contract does not exist");

    verifyCountersFailedCheck();
  }

  @Test
  public void testPermissioningContractCorrupt() throws IOException {
    final NodeSmartContractPermissioningController controller =
        setupController(
            "/NodeSmartContractPermissioningControllerTest/corruptSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    verifyCountersUntouched();

    assertThatThrownBy(
            () ->
                controller.isPermitted(
                    EnodeURL.fromString(
                        "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab61@[1:2:3:4:5:6:7:8]:30303"),
                    EnodeURL.fromString(
                        "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab63@[1:2:3:4:5:6:7:8]:30304")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Permissioning transaction failed when processing");

    verifyCountersFailedCheck();
  }
}
