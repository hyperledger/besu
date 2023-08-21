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
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration.Implementation.LAYERED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration.Implementation.LEGACY;

import org.hyperledger.besu.cli.options.AbstractCLIOptionsTest;
import org.hyperledger.besu.cli.options.OptionParser;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.util.number.Percentage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TransactionPoolOptionsTest
    extends AbstractCLIOptionsTest<TransactionPoolConfiguration, TransactionPoolOptions> {

  @Test
  public void strictTxReplayProtection_enabled() {
    internalTestSuccess(
        config -> assertThat(config.getStrictTransactionReplayProtectionEnabled()).isTrue(),
        "--strict-tx-replay-protection-enabled");
  }

  @Test
  public void strictTxReplayProtection_enabledWithBooleanArg() {
    internalTestSuccess(
        config -> assertThat(config.getStrictTransactionReplayProtectionEnabled()).isTrue(),
        "--strict-tx-replay-protection-enabled=true");
  }

  @Test
  public void strictTxReplayProtection_disabled() {
    internalTestSuccess(
        config -> assertThat(config.getStrictTransactionReplayProtectionEnabled()).isFalse(),
        "--strict-tx-replay-protection-enabled=false");
  }

  @Test
  public void strictTxReplayProtection_default() {
    internalTestSuccess(
        config -> assertThat(config.getStrictTransactionReplayProtectionEnabled()).isFalse());
  }

  @Test
  public void pendingTransactionRetentionPeriod() {
    final int pendingTxRetentionHours = 999;
    internalTestSuccess(
        config ->
            assertThat(config.getPendingTxRetentionPeriod()).isEqualTo(pendingTxRetentionHours),
        "--tx-pool-retention-hours",
        String.valueOf(pendingTxRetentionHours),
        "--tx-pool=legacy");
  }

  @Test
  public void disableLocalsDefault() {
    internalTestSuccess(config -> assertThat(config.getDisableLocalTransactions()).isFalse());
  }

  @Test
  public void disableLocalsOn() {
    internalTestSuccess(
        config -> assertThat(config.getDisableLocalTransactions()).isTrue(),
        "--tx-pool-disable-locals=true");
  }

  @Test
  public void disableLocalsOff() {
    internalTestSuccess(
        config -> assertThat(config.getDisableLocalTransactions()).isFalse(),
        "--tx-pool-disable-locals=false");
  }

  @Test
  public void saveToFileDisabledByDefault() {
    internalTestSuccess(config -> assertThat(config.getEnableSaveRestore()).isFalse());
  }

  @Test
  public void saveToFileEnabledDefaultPath() {
    internalTestSuccess(
        config -> assertThat(config.getEnableSaveRestore()).isTrue(),
        "--tx-pool-enable-save-restore=true");
  }

  @Test
  public void saveToFileEnabledCustomPath() {
    internalTestSuccess(
        config -> {
          assertThat(config.getEnableSaveRestore()).isTrue();
          assertThat(config.getSaveFile()).hasName("my.save.file");
        },
        "--tx-pool-enable-save-restore=true",
        "--tx-pool-save-file=my.save.file");
  }

  @Test
  public void senderLimited_derived() {
    internalTestSuccess(
        config -> assertThat(config.getTxPoolMaxFutureTransactionByAccount()).isEqualTo(9),
        "--tx-pool-limit-by-account-percentage=0.002",
        "--tx-pool=legacy");
  }

  @Test
  public void senderLimitedFloor_derived() {
    internalTestSuccess(
        config -> assertThat(config.getTxPoolMaxFutureTransactionByAccount()).isEqualTo(1),
        "--tx-pool-limit-by-account-percentage=0.0001",
        "--tx-pool=legacy");
  }

  @Test
  public void senderLimitedCeiling_violated() {
    internalTestFailure(
        "Invalid value for option '--tx-pool-limit-by-account-percentage'",
        "--tx-pool-limit-by-account-percentage=1.00002341",
        "--tx-pool=legacy");
  }

  @Test
  public void priceBump() {
    final Percentage priceBump = Percentage.fromInt(13);
    internalTestSuccess(
        config -> assertThat(config.getPriceBump()).isEqualTo(priceBump),
        "--tx-pool-price-bump",
        priceBump.toString());
  }

  @Test
  public void invalidPriceBumpShouldFail() {
    internalTestFailure(
        "Invalid value: 101, should be a number between 0 and 100 inclusive",
        "--tx-pool-price-bump",
        "101");
  }

  @Test
  public void txFeeCap() {
    final Wei txFeeCap = Wei.fromEth(2);
    internalTestSuccess(
        config -> assertThat(config.getTxFeeCap()).isEqualTo(txFeeCap),
        "--rpc-tx-feecap",
        OptionParser.format(txFeeCap));
  }

  @Test
  public void invalidTxFeeCapShouldFail() {
    internalTestFailure(
        "Invalid value for option '--rpc-tx-feecap'",
        "cannot convert 'abcd' to Wei",
        "--rpc-tx-feecap",
        "abcd");
  }

  @Test
  public void selectLayeredImplementationByDefault() {
    internalTestSuccess(config -> assertThat(config.getTxPoolImplementation()).isEqualTo(LAYERED));
  }

  @Test
  public void selectLayeredImplementationByArg() {
    internalTestSuccess(
        config -> assertThat(config.getTxPoolImplementation()).isEqualTo(LAYERED),
        "--tx-pool=layered");
  }

  @Test
  public void selectLegacyImplementationByArg() {
    internalTestSuccess(
        config -> assertThat(config.getTxPoolImplementation()).isEqualTo(LEGACY),
        "--tx-pool=legacy");
  }

  @Test
  public void failIfLegacyOptionsWhenLayeredSelectedByDefault() {
    internalTestFailure(
        "Could not use legacy transaction pool options with layered implementation",
        "--tx-pool-max-size=1000");
  }

  @Test
  public void failIfLegacyOptionsWhenLayeredSelectedByArg() {
    internalTestFailure(
        "Could not use legacy transaction pool options with layered implementation",
        "--tx-pool=layered",
        "--tx-pool-max-size=1000");
  }

  @Test
  public void failIfLayeredOptionsWhenLegacySelectedByArg() {
    internalTestFailure(
        "Could not use layered transaction pool options with legacy implementation",
        "--tx-pool=legacy",
        "--tx-pool-max-prioritized=1000");
  }

  @Override
  protected TransactionPoolConfiguration createDefaultDomainObject() {
    return TransactionPoolConfiguration.DEFAULT;
  }

  @Override
  protected TransactionPoolConfiguration createCustomizedDomainObject() {
    return ImmutableTransactionPoolConfiguration.builder()
        .strictTransactionReplayProtectionEnabled(true)
        .txPoolImplementation(LAYERED)
        .pendingTransactionsLayerMaxCapacityBytes(1_000_000L)
        .maxPrioritizedTransactions(1000)
        .maxFutureBySender(10)
        .build();
  }

  @Override
  protected TransactionPoolOptions optionsFromDomainObject(
      final TransactionPoolConfiguration domainObject) {
    return TransactionPoolOptions.fromConfig(domainObject);
  }

  @Override
  protected TransactionPoolOptions getOptionsFromBesuCommand(final TestBesuCommand besuCommand) {
    return besuCommand.getStableTransactionPoolOptions();
  }
}
