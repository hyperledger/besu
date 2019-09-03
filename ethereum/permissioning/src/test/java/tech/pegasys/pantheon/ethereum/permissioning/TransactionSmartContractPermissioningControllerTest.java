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
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.transaction.TransactionSimulator;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.plugin.services.metrics.Counter;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.math.BigInteger;

import com.google.common.io.Resources;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TransactionSmartContractPermissioningControllerTest {
  @Mock private MetricsSystem metricsSystem;
  @Mock private Counter checkCounter;
  @Mock private Counter checkPermittedCounter;
  @Mock private Counter checkUnpermittedCounter;

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

    when(metricsSystem.createCounter(
            PantheonMetricCategory.PERMISSIONING,
            "transaction_smart_contract_check_count",
            "Number of times the transaction smart contract permissioning provider has been checked"))
        .thenReturn(checkCounter);

    when(metricsSystem.createCounter(
            PantheonMetricCategory.PERMISSIONING,
            "transaction_smart_contract_check_count_permitted",
            "Number of times the transaction smart contract permissioning provider has been checked and returned permitted"))
        .thenReturn(checkPermittedCounter);

    when(metricsSystem.createCounter(
            PantheonMetricCategory.PERMISSIONING,
            "transaction_smart_contract_check_count_unpermitted",
            "Number of times the transaction smart contract permissioning provider has been checked and returned unpermitted"))
        .thenReturn(checkUnpermittedCounter);

    return new TransactionSmartContractPermissioningController(contractAddress, ts, metricsSystem);
  }

  private Transaction transactionForAccount(final Address address) {
    return Transaction.builder()
        .sender(address)
        .value(Wei.ZERO)
        .gasPrice(Wei.ZERO)
        .gasLimit(0)
        .payload(BytesValue.fromHexString("0x1234"))
        .nonce(1)
        .signature(Signature.create(BigInteger.ONE, BigInteger.TEN, (byte) 1))
        .build();
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
  public void testAccountIncluded() throws IOException {
    final TransactionSmartContractPermissioningController controller =
        setupController(
            "/TransactionSmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    verifyCountersUntouched();

    assertThat(controller.isPermitted(transactionForAccount(Address.fromHexString("0x1"))))
        .isTrue();

    verifyCountersPermitted();
  }

  @Test
  public void testAccountNotIncluded() throws IOException {
    final TransactionSmartContractPermissioningController controller =
        setupController(
            "/TransactionSmartContractPermissioningControllerTest/preseededSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    verifyCountersUntouched();

    assertThat(controller.isPermitted(transactionForAccount(Address.fromHexString("0x2"))))
        .isFalse();

    verifyCountersUnpermitted();
  }

  @Test
  public void testPermissioningContractMissing() throws IOException {
    final TransactionSmartContractPermissioningController controller =
        setupController(
            "/TransactionSmartContractPermissioningControllerTest/noSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    verifyCountersUntouched();

    assertThat(controller.isPermitted(transactionForAccount(Address.fromHexString("0x1"))))
        .isTrue();

    verifyCountersPermitted();
  }

  @Test
  public void testPermissioningContractCorrupt() throws IOException {
    final TransactionSmartContractPermissioningController controller =
        setupController(
            "/TransactionSmartContractPermissioningControllerTest/corruptSmartPermissioning.json",
            "0x0000000000000000000000000000000000001234");

    verifyCountersUntouched();

    assertThatThrownBy(
            () -> controller.isPermitted(transactionForAccount(Address.fromHexString("0x1"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Transaction permissioning transaction failed when processing");

    verifyCountersFailedCheck();
  }
}
