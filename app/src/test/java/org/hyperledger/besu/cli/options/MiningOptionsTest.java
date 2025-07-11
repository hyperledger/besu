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
package org.hyperledger.besu.cli.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.MiningConfiguration.DEFAULT_NON_POA_BLOCK_TXS_SELECTION_MAX_TIME;
import static org.hyperledger.besu.ethereum.core.MiningConfiguration.DEFAULT_POA_BLOCK_TXS_SELECTION_MAX_TIME;
import static org.hyperledger.besu.ethereum.core.MiningConfiguration.Unstable.DEFAULT_POS_BLOCK_CREATION_MAX_TIME;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.ImmutableMiningConfiguration;
import org.hyperledger.besu.ethereum.core.ImmutableMiningConfiguration.MutableInitValues;
import org.hyperledger.besu.ethereum.core.ImmutableMiningConfiguration.Unstable;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.util.number.PositiveNumber;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MiningOptionsTest extends AbstractCLIOptionsTest<MiningConfiguration, MiningOptions> {

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
  public void targetGasLimitIsEnabledWhenSpecified() {
    internalTestSuccess(
        miningParams ->
            assertThat(miningParams.getTargetGasLimit().getAsLong()).isEqualTo(10_000_000L),
        "--target-gas-limit=10000000");
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

  @Test
  public void blockTxsSelectionMaxTimeDefaultValue() {
    internalTestSuccess(
        this::runtimeConfiguration,
        miningParams ->
            assertThat(miningParams.getNonPoaBlockTxsSelectionMaxTime())
                .isEqualTo(DEFAULT_NON_POA_BLOCK_TXS_SELECTION_MAX_TIME));
  }

  @Test
  public void poaBlockTxsSelectionMaxTimeDefaultValue() {
    internalTestSuccess(
        this::runtimeConfiguration,
        miningParams ->
            assertThat(miningParams.getPoaBlockTxsSelectionMaxTime())
                .isEqualTo(DEFAULT_POA_BLOCK_TXS_SELECTION_MAX_TIME));
  }

  @Test
  public void poaBlockTxsSelectionMaxTimeOption() throws IOException {
    final Path genesisFileIBFT2 = createFakeGenesisFile(VALID_GENESIS_IBFT2_POST_LONDON);
    internalTestSuccess(
        this::runtimeConfiguration,
        miningParams ->
            assertThat(miningParams.getPoaBlockTxsSelectionMaxTime())
                .isEqualTo(PositiveNumber.fromInt(80)),
        "--genesis-file",
        genesisFileIBFT2.toString(),
        "--poa-block-txs-selection-max-time",
        "80");
  }

  @Test
  public void poaBlockTxsSelectionMaxTimeOptionOver100Percent() throws IOException {
    final Path genesisFileClique = createFakeGenesisFile(VALID_GENESIS_CLIQUE_POST_LONDON);
    internalTestSuccess(
        this::runtimeConfiguration,
        miningParams -> {
          assertThat(miningParams.getPoaBlockTxsSelectionMaxTime())
              .isEqualTo(PositiveNumber.fromInt(200));
          assertThat(miningParams.getBlockTxsSelectionMaxTime())
              .isEqualTo(Duration.ofSeconds(POA_BLOCK_PERIOD_SECONDS * 2).toMillis());
        },
        "--genesis-file",
        genesisFileClique.toString(),
        "--poa-block-txs-selection-max-time",
        "200");
  }

  @Test
  public void poaBlockTxsSelectionMaxTimeOnlyCompatibleWithPoaNetworks() {
    internalTestFailure(
        "--poa-block-txs-selection-max-time can be only used with PoA networks, see --block-txs-selection-max-time instead",
        "--poa-block-txs-selection-max-time",
        "90");
  }

  @Test
  public void extraDataDefaultValueIsBesuVersion() {
    final var expectedRegex = "besu \\d+\\.\\d+(\\.\\d+|\\-develop\\-\\p{XDigit}+)";
    internalTestSuccess(
        this::runtimeConfiguration,
        miningParams -> {
          assertThat(new String(miningParams.getExtraData().toArray(), StandardCharsets.UTF_8))
              .matches(expectedRegex);
        });
  }

  @Override
  protected MiningConfiguration createDefaultDomainObject() {
    return MiningConfiguration.newDefault();
  }

  @Override
  protected MiningConfiguration createCustomizedDomainObject() {
    return ImmutableMiningConfiguration.builder()
        .mutableInitValues(
            MutableInitValues.builder()
                .extraData(Bytes.fromHexString("0xabc321"))
                .minBlockOccupancyRatio(0.5)
                .coinbase(Address.ZERO)
                .build())
        .unstable(Unstable.builder().posBlockCreationMaxTime(1000).build())
        .build();
  }

  @Override
  protected MiningOptions optionsFromDomainObject(final MiningConfiguration domainObject) {
    return MiningOptions.fromConfig(domainObject);
  }

  @Override
  protected MiningOptions getOptionsFromBesuCommand(final TestBesuCommand besuCommand) {
    return besuCommand.getMiningOptions();
  }

  @Override
  protected String[] getNonOptionFields() {
    return new String[] {"transactionSelectionService"};
  }

  private MiningConfiguration runtimeConfiguration(
      final TestBesuCommand besuCommand, final MiningConfiguration miningConfiguration) {
    if (besuCommand.getGenesisConfigOptions().isPoa()) {
      miningConfiguration.setBlockPeriodSeconds(POA_BLOCK_PERIOD_SECONDS);
    }
    return miningConfiguration;
  }
}
