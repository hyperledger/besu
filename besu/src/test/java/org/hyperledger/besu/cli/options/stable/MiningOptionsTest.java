/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.cli.options.stable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.MiningParameters.Unstable.DEFAULT_POS_BLOCK_CREATION_MAX_TIME;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.cli.options.AbstractCLIOptionsTest;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.ImmutableMiningParameters;
import org.hyperledger.besu.ethereum.core.ImmutableMiningParameters.Unstable;
import org.hyperledger.besu.ethereum.core.ImmutableMiningParameters.UpdatableInitValues;
import org.hyperledger.besu.ethereum.core.MiningParameters;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MiningOptionsTest extends AbstractCLIOptionsTest<MiningParameters, MiningOptions> {

  @Test
  public void besuDoesNotStartInMiningModeIfCoinbaseNotSet() {
    internalTestFailure(
        "Unable to mine without a valid coinbase. Either disable mining (remove --miner-enabled) or specify the beneficiary of mining (via --miner-coinbase <Address>)",
        "--miner-enabled");
  }

  @Test
  public void miningIsEnabledWhenSpecified() {
    final String coinbaseStr = String.format("%040x", 1);
    internalTestSuccess(
        miningOpts -> {
          assertThat(miningOpts.isMiningEnabled()).isTrue();
          assertThat(miningOpts.getCoinbase())
              .isEqualTo(Optional.of(Address.fromHexString(coinbaseStr)));
        },
        "--miner-enabled",
        "--miner-coinbase=" + coinbaseStr);
  }

  @Test
  public void stratumMiningIsEnabledWhenSpecified() {
    final String coinbaseStr = String.format("%040x", 1);
    internalTestSuccess(
        miningOpts -> {
          assertThat(miningOpts.isMiningEnabled()).isTrue();
          assertThat(miningOpts.getCoinbase())
              .isEqualTo(Optional.of(Address.fromHexString(coinbaseStr)));
          assertThat(miningOpts.isStratumMiningEnabled()).isTrue();
        },
        "--miner-enabled",
        "--miner-coinbase=" + coinbaseStr,
        "--miner-stratum-enabled");
  }

  @Test
  public void stratumMiningOptionsRequiresServiceToBeEnabled() {
    internalTestFailure(
        "Unable to mine with Stratum if mining is disabled. Either disable Stratum mining (remove --miner-stratum-enabled) or specify mining is enabled (--miner-enabled)",
        "--network",
        "dev",
        "--miner-stratum-enabled");
  }

  @Test
  public void stratumMiningOptionsRequiresServiceToBeEnabledToml() throws IOException {
    final Path toml = createTempFile("toml", "miner-stratum-enabled=true\n");
    internalTestFailure(
        "Unable to mine with Stratum if mining is disabled. Either disable Stratum mining (remove --miner-stratum-enabled) or specify mining is enabled (--miner-enabled)",
        "--network",
        "dev",
        "--config-file",
        toml.toString());
  }

  @Test
  public void blockProducingOptionsWarnsMinerShouldBeEnabled() {
    final Address requestedCoinbase = Address.fromHexString("0000011111222223333344444");
    internalTestSuccess(
        miningOpts ->
            verifyOptionsConstraintLoggerCall(
                "--miner-enabled", "--miner-coinbase", "--min-gas-price", "--miner-extra-data"),
        "--network",
        "dev",
        "--miner-coinbase",
        requestedCoinbase.toString(),
        "--min-gas-price",
        "42",
        "--miner-extra-data",
        "0x1122334455667788990011223344556677889900112233445566778899001122");
  }

  @Test
  public void blockProducingOptionsWarnsMinerShouldBeEnabledToml() throws IOException {

    final Address requestedCoinbase = Address.fromHexString("0000011111222223333344444");

    final Path toml =
        createTempFile(
            "toml",
            "network=\"dev\"\n"
                + "miner-coinbase=\""
                + requestedCoinbase
                + "\"\n"
                + "min-gas-price=42\n"
                + "miner-extra-data=\"0x1122334455667788990011223344556677889900112233445566778899001122\"\n");

    internalTestSuccess(
        miningOpts ->
            verifyOptionsConstraintLoggerCall(
                "--miner-enabled", "--miner-coinbase", "--min-gas-price", "--miner-extra-data"),
        "--config-file",
        toml.toString());
  }

  @Test
  public void blockProducingOptionsDoNotWarnWhenPoAQBFT() throws IOException {

    final Path genesisFileQBFT = createFakeGenesisFile(VALID_GENESIS_QBFT_POST_LONDON);
    internalTestSuccess(
        miningOpts ->
            verify(mockLogger, atMost(0))
                .warn(
                    stringArgumentCaptor.capture(),
                    stringArgumentCaptor.capture(),
                    stringArgumentCaptor.capture()),
        "--genesis-file",
        genesisFileQBFT.toString(),
        "--min-gas-price",
        "42",
        "--miner-extra-data",
        "0x1122334455667788990011223344556677889900112233445566778899001122");
  }

  @Test
  public void blockProducingOptionsDoNotWarnWhenPoAIBFT2() throws IOException {

    final Path genesisFileIBFT2 = createFakeGenesisFile(VALID_GENESIS_IBFT2_POST_LONDON);
    internalTestSuccess(
        miningOpts ->
            verify(mockLogger, atMost(0))
                .warn(
                    stringArgumentCaptor.capture(),
                    stringArgumentCaptor.capture(),
                    stringArgumentCaptor.capture()),
        "--genesis-file",
        genesisFileIBFT2.toString(),
        "--min-gas-price",
        "42",
        "--miner-extra-data",
        "0x1122334455667788990011223344556677889900112233445566778899001122");
  }

  @Test
  public void blockProducingOptionsDoNotWarnWhenMergeEnabled() {

    final Address requestedCoinbase = Address.fromHexString("0000011111222223333344444");
    internalTestSuccess(
        miningOpt ->
            verify(mockLogger, atMost(0))
                .warn(
                    stringArgumentCaptor.capture(),
                    stringArgumentCaptor.capture(),
                    stringArgumentCaptor.capture()),
        "--miner-coinbase",
        requestedCoinbase.toString(),
        "--min-gas-price",
        "42",
        "--miner-extra-data",
        "0x1122334455667788990011223344556677889900112233445566778899001122");
  }

  @Test
  public void minGasPriceRequiresMainOption() {
    internalTestSuccess(
        miningOpt -> verifyOptionsConstraintLoggerCall("--miner-enabled", "--min-gas-price"),
        "--min-gas-price",
        "0",
        "--network",
        "dev");
  }

  @Test
  public void minGasPriceRequiresMainOptionToml() throws IOException {
    final Path toml = createTempFile("toml", "min-gas-price=0\nnetwork=\"dev\"\n");
    internalTestSuccess(
        miningOpt -> verifyOptionsConstraintLoggerCall("--miner-enabled", "--min-gas-price"),
        "--config-file",
        toml.toString());
  }

  @Test
  public void minGasPriceDoesNotRequireMainOptionWhenPoAQBFT() throws IOException {
    final Path genesisFileQBFT = createFakeGenesisFile(VALID_GENESIS_QBFT_POST_LONDON);
    internalTestSuccess(
        miningOpt ->
            verify(mockLogger, atMost(0))
                .warn(
                    stringArgumentCaptor.capture(),
                    stringArgumentCaptor.capture(),
                    stringArgumentCaptor.capture()),
        "--genesis-file",
        genesisFileQBFT.toString(),
        "--min-gas-price",
        "0");
  }

  @Test
  public void minGasPriceDoesNotRequireMainOptionWhenPoAIBFT2() throws IOException {
    final Path genesisFileIBFT2 = createFakeGenesisFile(VALID_GENESIS_IBFT2_POST_LONDON);

    internalTestSuccess(
        miningOpt ->
            verify(mockLogger, atMost(0))
                .warn(
                    stringArgumentCaptor.capture(),
                    stringArgumentCaptor.capture(),
                    stringArgumentCaptor.capture()),
        "--genesis-file",
        genesisFileIBFT2.toString(),
        "--min-gas-price",
        "0");
  }

  @Test
  public void miningParametersAreCaptured() {
    final Address requestedCoinbase = Address.fromHexString("0000011111222223333344444");
    final String extraDataString =
        "0x1122334455667788990011223344556677889900112233445566778899001122";
    internalTestSuccess(
        miningParams -> {
          assertThat(miningParams.getCoinbase()).isEqualTo(Optional.of(requestedCoinbase));
          assertThat(miningParams.getMinTransactionGasPrice()).isEqualTo(Wei.of(15));
          assertThat(miningParams.getExtraData()).isEqualTo(Bytes.fromHexString(extraDataString));
        },
        "--miner-enabled",
        "--miner-coinbase=" + requestedCoinbase.toString(),
        "--min-gas-price=15",
        "--miner-extra-data=" + extraDataString);
  }

  @Test
  public void targetGasLimitIsEnabledWhenSpecified() {
    internalTestSuccess(
        miningParams ->
            assertThat(miningParams.getTargetGasLimit().getAsLong()).isEqualTo(10_000_000L),
        "--target-gas-limit=10000000");
  }

  @Test
  public void targetGasLimitIsDisabledWhenNotSpecified() {
    internalTestSuccess(
        miningParams -> {
          final ArgumentCaptor<GasLimitCalculator> gasLimitCalculatorArgumentCaptor =
              ArgumentCaptor.forClass(GasLimitCalculator.class);

          verify(mockControllerBuilder)
              .gasLimitCalculator(gasLimitCalculatorArgumentCaptor.capture());
          assertThat(gasLimitCalculatorArgumentCaptor.getValue())
              .isEqualTo(GasLimitCalculator.constant());
        });
  }

  @Test
  public void posBlockCreationMaxTimeDefaultValue() {
    internalTestSuccess(
        miningParams ->
            assertThat(miningParams.getUnstable().getPosBlockCreationMaxTime())
                .isEqualTo(DEFAULT_POS_BLOCK_CREATION_MAX_TIME));
  }

  @Test
  public void posBlockCreationMaxTimeOption() {
    internalTestSuccess(
        miningParams ->
            assertThat(miningParams.getUnstable().getPosBlockCreationMaxTime()).isEqualTo(7000L),
        "--Xpos-block-creation-max-time",
        "7000");
  }

  @Test
  public void posBlockCreationMaxTimeOutOfAllowedRange() {
    internalTestFailure(
        "--Xpos-block-creation-max-time must be positive and ≤ 12000",
        "--Xpos-block-creation-max-time",
        "17000");
  }

  @Override
  protected MiningParameters createDefaultDomainObject() {
    return MiningParameters.newDefault();
  }

  @Override
  protected MiningParameters createCustomizedDomainObject() {
    return ImmutableMiningParameters.builder()
        .updatableInitValues(
            UpdatableInitValues.builder()
                .isMiningEnabled(true)
                .extraData(Bytes.fromHexString("0xabc321"))
                .minBlockOccupancyRatio(0.5)
                .build())
        .coinbase(Address.ZERO)
        .isStratumMiningEnabled(true)
        .unstable(Unstable.builder().posBlockCreationMaxTime(1000).build())
        .build();
  }

  @Override
  protected MiningOptions optionsFromDomainObject(final MiningParameters domainObject) {
    return MiningOptions.fromConfig(domainObject);
  }

  @Override
  protected MiningOptions getOptionsFromBesuCommand(final TestBesuCommand besuCommand) {
    return besuCommand.getMiningOptions();
  }
}
