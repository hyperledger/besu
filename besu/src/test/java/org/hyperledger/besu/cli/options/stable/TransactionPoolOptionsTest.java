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
package org.hyperledger.besu.cli.options.stable;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration.Implementation.LAYERED;

import org.hyperledger.besu.cli.options.AbstractCLIOptionsTest;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TransactionPoolOptionsTest
    extends AbstractCLIOptionsTest<TransactionPoolConfiguration, TransactionPoolOptions> {

  @Test
  public void strictTxReplayProtection_enabled() {
    final TestBesuCommand cmd = parseCommand("--strict-tx-replay-protection-enabled");

    final TransactionPoolOptions options = getOptionsFromBesuCommand(cmd);
    final TransactionPoolConfiguration config = options.toDomainObject();
    assertThat(config.getStrictTransactionReplayProtectionEnabled()).isTrue();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void strictTxReplayProtection_enabledWithBooleanArg() {
    final TestBesuCommand cmd = parseCommand("--strict-tx-replay-protection-enabled=true");

    final TransactionPoolOptions options = getOptionsFromBesuCommand(cmd);
    final TransactionPoolConfiguration config = options.toDomainObject();
    assertThat(config.getStrictTransactionReplayProtectionEnabled()).isTrue();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void strictTxReplayProtection_disabled() {
    final TestBesuCommand cmd = parseCommand("--strict-tx-replay-protection-enabled=false");

    final TransactionPoolOptions options = getOptionsFromBesuCommand(cmd);
    final TransactionPoolConfiguration config = options.toDomainObject();
    assertThat(config.getStrictTransactionReplayProtectionEnabled()).isFalse();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void strictTxReplayProtection_default() {
    final TestBesuCommand cmd = parseCommand();

    final TransactionPoolOptions options = getOptionsFromBesuCommand(cmd);
    final TransactionPoolConfiguration config = options.toDomainObject();
    assertThat(config.getStrictTransactionReplayProtectionEnabled()).isFalse();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Override
  protected TransactionPoolConfiguration createDefaultDomainObject() {
    final ImmutableTransactionPoolConfiguration defaultValue =
        ImmutableTransactionPoolConfiguration.builder().build();
    return ImmutableTransactionPoolConfiguration.builder()
        .from(defaultValue)
        .strictTransactionReplayProtectionEnabled(false)
        .txPoolImplementation(defaultValue.getTxPoolImplementation())
        .pendingTransactionsLayerMaxCapacityBytes(
            defaultValue.getPendingTransactionsLayerMaxCapacityBytes())
        .maxPrioritizedTransactions(defaultValue.getMaxPrioritizedTransactions())
        .maxFutureBySender(defaultValue.getMaxFutureBySender())
        .build();
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
