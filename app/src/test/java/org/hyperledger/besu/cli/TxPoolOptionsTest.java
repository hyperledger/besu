/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.cli.BesuCommandTest.GENESIS_WITH_ZERO_BASE_FEE_MARKET;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.util.number.Percentage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class TxPoolOptionsTest extends CommandTestAbstract {

  @Test
  public void txpoolDefaultSaveFileRelativeToDataPath() throws IOException {
    final Path dataDir = Files.createTempDirectory("data-dir");
    parseCommand("--data-path", dataDir.toString(), "--tx-pool-enable-save-restore", "true");
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());

    assertThat(transactionPoolConfigCaptor.getValue().getSaveFile())
        .isEqualTo(dataDir.resolve(TransactionPoolConfiguration.DEFAULT_SAVE_FILE_NAME).toFile());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void txpoolCustomSaveFileRelativeToDataPath() throws IOException {
    final Path dataDir = Files.createTempDirectory("data-dir");
    dataDir.toFile().deleteOnExit();
    final File saveFile = Files.createTempFile(dataDir, "txpool", "save").toFile();
    saveFile.deleteOnExit();
    parseCommand(
        "--data-path",
        dataDir.toString(),
        "--tx-pool-enable-save-restore",
        "true",
        "--tx-pool-save-file",
        saveFile.getName());
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());

    final File configuredSaveFile = transactionPoolConfigCaptor.getValue().getSaveFile();
    assertThat(configuredSaveFile).isEqualTo(saveFile);
    assertThat(configuredSaveFile.toPath().getParent()).isEqualTo(dataDir);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void txpoolSaveFileAbsolutePathOutsideDataPath() throws IOException {
    final Path dataDir = Files.createTempDirectory("data-dir");
    dataDir.toFile().deleteOnExit();
    final File saveFile = File.createTempFile("txpool", "dump");
    saveFile.deleteOnExit();
    parseCommand(
        "--data-path",
        dataDir.toString(),
        "--tx-pool-enable-save-restore",
        "true",
        "--tx-pool-save-file",
        saveFile.getAbsolutePath());
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());

    final File configuredSaveFile = transactionPoolConfigCaptor.getValue().getSaveFile();
    assertThat(configuredSaveFile).isEqualTo(saveFile);
    assertThat(configuredSaveFile.toPath().getParent()).isNotEqualTo(dataDir);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  @Disabled // Failing in CI, but not locally
  public void txpoolForcePriceBumpToZeroWhenZeroBaseFeeMarket() throws IOException {
    final Path genesisFile = createFakeGenesisFile(GENESIS_WITH_ZERO_BASE_FEE_MARKET);
    parseCommand("--genesis-file", genesisFile.toString());
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());

    final Percentage priceBump = transactionPoolConfigCaptor.getValue().getPriceBump();
    assertThat(priceBump).isEqualTo(Percentage.ZERO);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void txpoolPriceBumpOptionIncompatibleWithZeroWhenZeroBaseFeeMarket() throws IOException {
    final Path genesisFile = createFakeGenesisFile(GENESIS_WITH_ZERO_BASE_FEE_MARKET);
    parseCommand("--genesis-file", genesisFile.toString(), "--tx-pool-price-bump", "5");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Price bump option is not compatible with zero base fee market");
  }

  @Test
  public void txpoolForcePriceBumpToZeroWhenMinGasPriceZero() {
    parseCommand("--min-gas-price", "0");
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());

    final Percentage priceBump = transactionPoolConfigCaptor.getValue().getPriceBump();
    assertThat(priceBump).isEqualTo(Percentage.ZERO);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void txpoolPriceBumpKeepItsValueIfSetEvenWhenMinGasPriceZero() {
    parseCommand("--min-gas-price", "0", "--tx-pool-price-bump", "1");
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());

    final Percentage priceBump = transactionPoolConfigCaptor.getValue().getPriceBump();
    assertThat(priceBump).isEqualTo(Percentage.fromInt(1));

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void txpoolWhenNotSetForceTxPoolMinGasPriceToZeroWhenMinGasPriceZero() {
    parseCommand("--min-gas-price", "0");
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());

    final Wei txPoolMinGasPrice = transactionPoolConfigCaptor.getValue().getMinGasPrice();
    assertThat(txPoolMinGasPrice).isEqualTo(Wei.ZERO);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    verify(mockLogger, atLeast(1))
        .warn(
            contains(
                "Forcing tx-pool-min-gas-price=0, since it cannot be greater than the value of min-gas-price"));
  }

  @Test
  public void txpoolTxPoolMinGasPriceMustNotBeGreaterThanMinGasPriceZero() {
    parseCommand("--min-gas-price", "100", "--tx-pool-min-gas-price", "101");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("tx-pool-min-gas-price cannot be greater than the value of min-gas-price");
  }
}
