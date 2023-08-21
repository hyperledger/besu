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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration.Implementation.LAYERED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration.Implementation.LEGACY;

import org.hyperledger.besu.cli.options.AbstractCLIOptionsTest;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;

import java.util.function.Consumer;

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

  private void internalTestSuccess(
      final Consumer<TransactionPoolConfiguration> assertion, final String... args) {
    final TestBesuCommand cmd = parseCommand(args);

    final TransactionPoolOptions options = getOptionsFromBesuCommand(cmd);
    final TransactionPoolConfiguration config = options.toDomainObject();
    assertion.accept(config);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  private void internalTestFailure(final String errorMsg, final String... args) {
    parseCommand(args);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).contains(errorMsg);
  }

  @Override
  protected TransactionPoolConfiguration createDefaultDomainObject() {
    return ImmutableTransactionPoolConfiguration.builder().build();
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
