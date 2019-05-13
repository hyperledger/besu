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
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.transaction.TransactionSimulator;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;

import com.google.common.io.Resources;
import org.junit.Test;

public class TransactionSmartContractPermissioningControllerTest {
  private TransactionSmartContractPermissioningController setupController(
      final String resourceName, final String contractAddressString) throws IOException {
    final ProtocolSchedule<Void> protocolSchedule = MainnetProtocolSchedule.create();

    final String emptyContractFile =
        Resources.toString(this.getClass().getResource(resourceName), UTF_8);
    final GenesisState genesisState =
        GenesisState.fromConfig(GenesisConfigFile.fromConfig(emptyContractFile), protocolSchedule);

    final MutableBlockchain blockchain = createInMemoryBlockchain(genesisState.getBlock());
    final WorldStateArchive worldArchive = createInMemoryWorldStateArchive();

    genesisState.writeStateTo(worldArchive.getMutable());

    final TransactionSimulator ts =
        new TransactionSimulator(blockchain, worldArchive, protocolSchedule);
    final Address contractAddress = Address.fromHexString(contractAddressString);

    return new TransactionSmartContractPermissioningController(contractAddress, ts);
  }

  private Transaction transactionForAccount(final Address address) {
    return Transaction.builder()
        .sender(address)
        .value(Wei.ZERO)
        .gasPrice(Wei.ZERO)
        .gasLimit(0)
        .payload(BytesValue.EMPTY)
        .build();
  }

  @Test
  public void testAccountIncluded() throws IOException {
    final TransactionSmartContractPermissioningController controller =
        setupController(
            "/TransactionSmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    assertThat(controller.isPermitted(transactionForAccount(Address.fromHexString("0x1"))))
        .isTrue();
  }

  @Test
  public void testAccountNotIncluded() throws IOException {
    final TransactionSmartContractPermissioningController controller =
        setupController(
            "/TransactionSmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    assertThat(controller.isPermitted(transactionForAccount(Address.fromHexString("0x2"))))
        .isFalse();
  }

  @Test
  public void testPermissioningContractMissing() throws IOException {
    final TransactionSmartContractPermissioningController controller =
        setupController(
            "/TransactionSmartContractPermissioningControllerTest/noSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    assertThatThrownBy(
            () -> controller.isPermitted(transactionForAccount(Address.fromHexString("0x1"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Transaction permissioning contract does not exist");
  }

  @Test
  public void testPermissioningContractCorrupt() throws IOException {
    final TransactionSmartContractPermissioningController controller =
        setupController(
            "/TransactionSmartContractPermissioningControllerTest/corruptSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    assertThatThrownBy(
            () -> controller.isPermitted(transactionForAccount(Address.fromHexString("0x1"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Transaction permissioning transaction failed when processing");
  }
}
