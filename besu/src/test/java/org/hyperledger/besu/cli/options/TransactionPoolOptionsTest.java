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
package org.hyperledger.besu.cli.options;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.cli.options.unstable.TransactionPoolOptions;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;

import java.time.Duration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TransactionPoolOptionsTest
    extends AbstractCLIOptionsTest<
        ImmutableTransactionPoolConfiguration.Builder, TransactionPoolOptions> {

  @Test
  public void strictTxReplayProtection_enabled() {
    final TestBesuCommand cmd = parseCommand("--strict-tx-replay-protection-enabled");

    final TransactionPoolOptions options = getOptionsFromBesuCommand(cmd);
    final TransactionPoolConfiguration config = options.toDomainObject().build();
    assertThat(config.getStrictTransactionReplayProtectionEnabled()).isTrue();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void strictTxReplayProtection_enabledWithBooleanArg() {
    final TestBesuCommand cmd = parseCommand("--strict-tx-replay-protection-enabled=true");

    final TransactionPoolOptions options = getOptionsFromBesuCommand(cmd);
    final TransactionPoolConfiguration config = options.toDomainObject().build();
    assertThat(config.getStrictTransactionReplayProtectionEnabled()).isTrue();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void strictTxReplayProtection_disabled() {
    final TestBesuCommand cmd = parseCommand("--strict-tx-replay-protection-enabled=false");

    final TransactionPoolOptions options = getOptionsFromBesuCommand(cmd);
    final TransactionPoolConfiguration config = options.toDomainObject().build();
    assertThat(config.getStrictTransactionReplayProtectionEnabled()).isFalse();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void strictTxReplayProtection_default() {
    final TestBesuCommand cmd = parseCommand();

    final TransactionPoolOptions options = getOptionsFromBesuCommand(cmd);
    final TransactionPoolConfiguration config = options.toDomainObject().build();
    assertThat(config.getStrictTransactionReplayProtectionEnabled()).isFalse();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void txMessageKeepAliveSeconds() {
    final int txMessageKeepAliveSeconds = 999;
    final TestBesuCommand cmd =
        parseCommand(
            "--Xincoming-tx-messages-keep-alive-seconds",
            String.valueOf(txMessageKeepAliveSeconds));

    final TransactionPoolOptions options = getOptionsFromBesuCommand(cmd);
    final TransactionPoolConfiguration config = options.toDomainObject().build();
    assertThat(config.getTxMessageKeepAliveSeconds()).isEqualTo(txMessageKeepAliveSeconds);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void eth65TrxAnnouncedBufferingPeriod() {
    final long eth65TrxAnnouncedBufferingPeriod = 999;
    final TestBesuCommand cmd =
        parseCommand(
            "--Xeth65-tx-announced-buffering-period-milliseconds",
            String.valueOf(eth65TrxAnnouncedBufferingPeriod));

    final TransactionPoolOptions options = getOptionsFromBesuCommand(cmd);
    final TransactionPoolConfiguration config = options.toDomainObject().build();
    assertThat(config.getEth65TrxAnnouncedBufferingPeriod())
        .hasMillis(eth65TrxAnnouncedBufferingPeriod);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Override
  ImmutableTransactionPoolConfiguration.Builder createDefaultDomainObject() {
    final ImmutableTransactionPoolConfiguration defaultValue =
        ImmutableTransactionPoolConfiguration.builder().build();
    return ImmutableTransactionPoolConfiguration.builder()
        .strictTransactionReplayProtectionEnabled(false)
        .txMessageKeepAliveSeconds(defaultValue.getTxMessageKeepAliveSeconds())
        .eth65TrxAnnouncedBufferingPeriod(defaultValue.getEth65TrxAnnouncedBufferingPeriod());
  }

  @Override
  ImmutableTransactionPoolConfiguration.Builder createCustomizedDomainObject() {
    return ImmutableTransactionPoolConfiguration.builder()
        .strictTransactionReplayProtectionEnabled(true)
        .txMessageKeepAliveSeconds(TransactionPoolConfiguration.DEFAULT_TX_MSG_KEEP_ALIVE + 1)
        .eth65TrxAnnouncedBufferingPeriod(
            TransactionPoolConfiguration.ETH65_TRX_ANNOUNCED_BUFFERING_PERIOD.plus(
                Duration.ofMillis(100)));
  }

  @Override
  TransactionPoolOptions optionsFromDomainObject(
      final ImmutableTransactionPoolConfiguration.Builder domainObject) {
    return TransactionPoolOptions.fromConfig(domainObject.build());
  }

  @Override
  TransactionPoolOptions getOptionsFromBesuCommand(final TestBesuCommand besuCommand) {
    return besuCommand.getTransactionPoolOptions();
  }
}
