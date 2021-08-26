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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

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
    final ProtocolSchedule protocolSchedule = ProtocolScheduleFixture.MAINNET;

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
            BesuMetricCategory.PERMISSIONING,
            "node_smart_contract_check_count",
            "Number of times the node smart contract permissioning provider has been checked"))
        .thenReturn(checkCounter);

    when(metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "node_smart_contract_check_count_permitted",
            "Number of times the node smart contract permissioning provider has been checked and returned permitted"))
        .thenReturn(checkPermittedCounter);

    when(metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
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
            controller.isConnectionPermitted(
                EnodeURLImpl.fromString(
                    "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:30303"),
                EnodeURLImpl.fromString(
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
            controller.isConnectionPermitted(
                EnodeURLImpl.fromString(
                    "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:30303"),
                EnodeURLImpl.fromString(
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
            controller.isConnectionPermitted(
                EnodeURLImpl.fromString(
                    "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:30302"),
                EnodeURLImpl.fromString(
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
            controller.isConnectionPermitted(
                EnodeURLImpl.fromString(
                    "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab61@[1:2:3:4:5:6:7:8]:30303"),
                EnodeURLImpl.fromString(
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
            controller.isConnectionPermitted(
                EnodeURLImpl.fromString(
                    "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab63@[1:2:3:4:5:6:7:8]:30303"),
                EnodeURLImpl.fromString(
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
            controller.isConnectionPermitted(
                EnodeURLImpl.fromString(
                    "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab61@[1:2:3:4:5:6:7:8]:30303"),
                EnodeURLImpl.fromString(
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
                controller.isConnectionPermitted(
                    EnodeURLImpl.fromString(
                        "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab61@[1:2:3:4:5:6:7:8]:30303"),
                    EnodeURLImpl.fromString(
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
                controller.isConnectionPermitted(
                    EnodeURLImpl.fromString(
                        "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab61@[1:2:3:4:5:6:7:8]:30303"),
                    EnodeURLImpl.fromString(
                        "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab63@[1:2:3:4:5:6:7:8]:30304")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Permissioning transaction failed when processing");

    verifyCountersFailedCheck();
  }
}
