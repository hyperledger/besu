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
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.io.IOException;
import java.math.BigInteger;

import com.google.common.io.Resources;
import org.apache.tuweni.bytes.Bytes;
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
    final ProtocolSchedule protocolSchedule = ProtocolScheduleFixture.MAINNET;

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
            BesuMetricCategory.PERMISSIONING,
            "transaction_smart_contract_check_count",
            "Number of times the transaction smart contract permissioning provider has been checked"))
        .thenReturn(checkCounter);

    when(metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "transaction_smart_contract_check_count_permitted",
            "Number of times the transaction smart contract permissioning provider has been checked and returned permitted"))
        .thenReturn(checkPermittedCounter);

    when(metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "transaction_smart_contract_check_count_unpermitted",
            "Number of times the transaction smart contract permissioning provider has been checked and returned unpermitted"))
        .thenReturn(checkUnpermittedCounter);

    return new TransactionSmartContractPermissioningController(contractAddress, ts, metricsSystem);
  }

  private Transaction transactionForAccount(final Address address) {
    return Transaction.builder()
        .type(TransactionType.FRONTIER)
        .sender(address)
        .value(Wei.ZERO)
        .gasPrice(Wei.ZERO)
        .gasLimit(0)
        .payload(Bytes.fromHexString("0x1234"))
        .nonce(1)
        .signature(
            SignatureAlgorithmFactory.getInstance()
                .createSignature(BigInteger.ONE, BigInteger.TEN, (byte) 1))
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
