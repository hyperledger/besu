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
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.transaction.TransactionSimulator;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.io.IOException;

import com.google.common.io.Resources;
import org.junit.Test;

public class SmartContractPermissioningControllerTest {

  private SmartContractPermissioningController setupController(
      final String resourceName, final String contractAddressString) throws IOException {
    final ProtocolSchedule<Void> protocolSchedule = MainnetProtocolSchedule.create();

    final String emptyContractFile = Resources.toString(Resources.getResource(resourceName), UTF_8);
    final GenesisState genesisState =
        GenesisState.fromConfig(GenesisConfigFile.fromConfig(emptyContractFile), protocolSchedule);

    final MutableBlockchain blockchain = createInMemoryBlockchain(genesisState.getBlock());
    final WorldStateArchive worldArchive = createInMemoryWorldStateArchive();

    genesisState.writeStateTo(worldArchive.getMutable());

    final TransactionSimulator ts =
        new TransactionSimulator(blockchain, worldArchive, protocolSchedule);
    final Address contractAddress = Address.fromHexString(contractAddressString);

    return new SmartContractPermissioningController(contractAddress, ts);
  }

  @Test
  public void testIpv4Included() throws IOException {
    final SmartContractPermissioningController controller =
        setupController(
            "SmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    assertThat(
            controller.isPermitted(
                EnodeURL.fromString(
                    "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:30303"),
                EnodeURL.fromString(
                    "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:30304")))
        .isTrue();
  }

  @Test
  public void testIpv4DestinationMissing() throws IOException {
    final SmartContractPermissioningController controller =
        setupController(
            "SmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    assertThat(
            controller.isPermitted(
                EnodeURL.fromString(
                    "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:30303"),
                EnodeURL.fromString(
                    "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:30305")))
        .isFalse();
  }

  @Test
  public void testIpv4SourceMissing() throws IOException {
    final SmartContractPermissioningController controller =
        setupController(
            "SmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    assertThat(
            controller.isPermitted(
                EnodeURL.fromString(
                    "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:30302"),
                EnodeURL.fromString(
                    "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.1:30304")))
        .isFalse();
  }

  @Test
  public void testIpv6Included() throws IOException {
    final SmartContractPermissioningController controller =
        setupController(
            "SmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    assertThat(
            controller.isPermitted(
                EnodeURL.fromString(
                    "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab61@[1:2:3:4:5:6:7:8]:30303"),
                EnodeURL.fromString(
                    "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab62@[1:2:3:4:5:6:7:8]:30304")))
        .isTrue();
  }

  @Test
  public void testIpv6SourceMissing() throws IOException {
    final SmartContractPermissioningController controller =
        setupController(
            "SmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    assertThat(
            controller.isPermitted(
                EnodeURL.fromString(
                    "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab63@[1:2:3:4:5:6:7:8]:30303"),
                EnodeURL.fromString(
                    "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab62@[1:2:3:4:5:6:7:8]:30304")))
        .isFalse();
  }

  @Test
  public void testIpv6DestinationMissing() throws IOException {
    final SmartContractPermissioningController controller =
        setupController(
            "SmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    assertThat(
            controller.isPermitted(
                EnodeURL.fromString(
                    "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab61@[1:2:3:4:5:6:7:8]:30303"),
                EnodeURL.fromString(
                    "enode://1234000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ab63@[1:2:3:4:5:6:7:8]:30304")))
        .isFalse();
  }
}
