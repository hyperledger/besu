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
package org.hyperledger.besu.ethereum.permissioning.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.SmartContractPermissioningConfiguration;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AccountPermissioningControllerFactoryTest {

  @Mock private TransactionSimulator transactionSimulator;
  @Mock private Blockchain blockchain;

  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @Test
  public void createWithNullPermissioningConfigShouldReturnEmpty() {
    Optional<AccountPermissioningController> controller =
        AccountPermissioningControllerFactory.create(
            null, transactionSimulator, metricsSystem, blockchain);

    Assertions.assertThat(controller).isEmpty();
  }

  @Test
  public void createLocalConfigWithAccountPermissioningDisabledShouldReturnEmpty() {
    LocalPermissioningConfiguration localConfig = LocalPermissioningConfiguration.createDefault();
    assertThat(localConfig.isAccountAllowlistEnabled()).isFalse();

    PermissioningConfiguration permissioningConfiguration =
        new PermissioningConfiguration(
            Optional.of(localConfig), Optional.empty(), Optional.empty());

    Optional<AccountPermissioningController> controller =
        AccountPermissioningControllerFactory.create(
            permissioningConfiguration, transactionSimulator, metricsSystem, blockchain);

    Assertions.assertThat(controller).isEmpty();
  }

  @Test
  public void createLocalConfigOnlyControllerShouldReturnExpectedController() {
    LocalPermissioningConfiguration localConfig = localConfig();
    assertThat(localConfig.isAccountAllowlistEnabled()).isTrue();

    PermissioningConfiguration permissioningConfiguration =
        new PermissioningConfiguration(
            Optional.of(localConfig), Optional.empty(), Optional.empty());

    Optional<AccountPermissioningController> controller =
        AccountPermissioningControllerFactory.create(
            permissioningConfiguration, transactionSimulator, metricsSystem, blockchain);

    Assertions.assertThat(controller).isNotEmpty();
    assertThat(controller.get().getAccountLocalConfigPermissioningController()).isNotEmpty();
    assertThat(controller.get().getTransactionSmartContractPermissioningController()).isEmpty();
  }

  @Test
  public void createOnchainConfigWithAccountPermissioningDisabledShouldReturnEmpty() {
    SmartContractPermissioningConfiguration onchainConfig =
        SmartContractPermissioningConfiguration.createDefault();
    assertThat(onchainConfig.isSmartContractAccountAllowlistEnabled()).isFalse();

    PermissioningConfiguration permissioningConfiguration =
        new PermissioningConfiguration(
            Optional.empty(), Optional.of(onchainConfig), Optional.empty());

    Optional<AccountPermissioningController> controller =
        AccountPermissioningControllerFactory.create(
            permissioningConfiguration, transactionSimulator, metricsSystem, blockchain);

    Assertions.assertThat(controller).isEmpty();
  }

  @Test
  public void createOnchainConfigOnlyControllerShouldReturnExpectedController() {
    SmartContractPermissioningConfiguration onchainConfig = onchainConfig();
    assertThat(onchainConfig.isSmartContractAccountAllowlistEnabled()).isTrue();

    PermissioningConfiguration permissioningConfiguration =
        new PermissioningConfiguration(
            Optional.empty(), Optional.of(onchainConfig), Optional.empty());

    Optional<AccountPermissioningController> controller =
        AccountPermissioningControllerFactory.create(
            permissioningConfiguration, transactionSimulator, metricsSystem, blockchain);

    Assertions.assertThat(controller).isNotEmpty();
    assertThat(controller.get().getAccountLocalConfigPermissioningController()).isEmpty();
    assertThat(controller.get().getTransactionSmartContractPermissioningController()).isNotEmpty();
  }

  @Test
  public void createOnchainShouldFailIfValidationFails() {
    SmartContractPermissioningConfiguration onchainConfig = onchainConfig();
    assertThat(onchainConfig.isSmartContractAccountAllowlistEnabled()).isTrue();

    PermissioningConfiguration permissioningConfiguration =
        new PermissioningConfiguration(
            Optional.empty(), Optional.of(onchainConfig), Optional.empty());

    when(transactionSimulator.processAtHead(any())).thenThrow(new RuntimeException());

    final Throwable thrown =
        catchThrowable(
            () ->
                AccountPermissioningControllerFactory.create(
                    permissioningConfiguration, transactionSimulator, metricsSystem, blockchain));

    assertThat(thrown)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Error validating onchain account permissioning smart contract configuration");
  }

  @Test
  public void createLocalAndOnchainControllerShouldReturnExpectedControllers() {
    LocalPermissioningConfiguration localConfig = localConfig();
    assertThat(localConfig.isAccountAllowlistEnabled()).isTrue();

    SmartContractPermissioningConfiguration onchainConfig = onchainConfig();
    assertThat(onchainConfig.isSmartContractAccountAllowlistEnabled()).isTrue();

    PermissioningConfiguration permissioningConfiguration =
        new PermissioningConfiguration(
            Optional.of(localConfig), Optional.of(onchainConfig), Optional.empty());

    Optional<AccountPermissioningController> controller =
        AccountPermissioningControllerFactory.create(
            permissioningConfiguration, transactionSimulator, metricsSystem, blockchain);

    Assertions.assertThat(controller).isNotEmpty();
    assertThat(controller.get().getAccountLocalConfigPermissioningController()).isNotEmpty();
    assertThat(controller.get().getTransactionSmartContractPermissioningController()).isNotEmpty();
  }

  private LocalPermissioningConfiguration localConfig() {
    LocalPermissioningConfiguration localPermissioningConfiguration =
        LocalPermissioningConfiguration.createDefault();
    localPermissioningConfiguration.setAccountAllowlist(
        Arrays.asList(Address.fromHexString("0x00").toString()));
    localPermissioningConfiguration.setAccountPermissioningConfigFilePath(
        createTempFile().getPath());
    return localPermissioningConfiguration;
  }

  private SmartContractPermissioningConfiguration onchainConfig() {
    SmartContractPermissioningConfiguration onchainPermissioningConfiguration =
        SmartContractPermissioningConfiguration.createDefault();
    onchainPermissioningConfiguration.setAccountSmartContractAddress(
        Address.fromHexString("0x0000000000000000000000000000000000008888"));
    onchainPermissioningConfiguration.setSmartContractAccountAllowlistEnabled(true);
    return onchainPermissioningConfiguration;
  }

  private File createTempFile() {
    try {
      File file = File.createTempFile("test", "test");
      file.deleteOnExit();
      return file;
    } catch (IOException e) {
      fail("Test failed to create temporary file", e);
    }
    return null;
  }
}
