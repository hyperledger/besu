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
package org.hyperledger.besu.cli.options.unstable;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.cli.options.AbstractCLIOptionsTest;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;

import java.time.Duration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TransactionPoolOptionsTest
    extends AbstractCLIOptionsTest<TransactionPoolConfiguration.Unstable, TransactionPoolOptions> {

  @Test
  public void txMessageKeepAliveSeconds() {
    final int txMessageKeepAliveSeconds = 999;
    final TestBesuCommand cmd =
        parseCommand(
            "--Xincoming-tx-messages-keep-alive-seconds",
            String.valueOf(txMessageKeepAliveSeconds));

    final TransactionPoolOptions options = getOptionsFromBesuCommand(cmd);
    final TransactionPoolConfiguration.Unstable config = options.toDomainObject();
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
    final TransactionPoolConfiguration.Unstable config = options.toDomainObject();
    assertThat(config.getEth65TrxAnnouncedBufferingPeriod())
        .hasMillis(eth65TrxAnnouncedBufferingPeriod);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Override
  protected TransactionPoolConfiguration.Unstable createDefaultDomainObject() {
    final ImmutableTransactionPoolConfiguration.Unstable defaultValue =
        ImmutableTransactionPoolConfiguration.Unstable.builder().build();
    return ImmutableTransactionPoolConfiguration.Unstable.builder()
        .from(defaultValue)
        .txMessageKeepAliveSeconds(defaultValue.getTxMessageKeepAliveSeconds())
        .eth65TrxAnnouncedBufferingPeriod(defaultValue.getEth65TrxAnnouncedBufferingPeriod())
        .build();
  }

  @Override
  protected TransactionPoolConfiguration.Unstable createCustomizedDomainObject() {
    return ImmutableTransactionPoolConfiguration.Unstable.builder()
        .txMessageKeepAliveSeconds(
            TransactionPoolConfiguration.Unstable.DEFAULT_TX_MSG_KEEP_ALIVE + 1)
        .eth65TrxAnnouncedBufferingPeriod(
            TransactionPoolConfiguration.Unstable.ETH65_TRX_ANNOUNCED_BUFFERING_PERIOD.plus(
                Duration.ofMillis(100)))
        .build();
  }

  @Override
  protected TransactionPoolOptions optionsFromDomainObject(
      final TransactionPoolConfiguration.Unstable domainObject) {
    return TransactionPoolOptions.fromConfig(domainObject);
  }

  @Override
  protected TransactionPoolOptions getOptionsFromBesuCommand(final TestBesuCommand besuCommand) {
    return besuCommand.getUnstableTransactionPoolOptions();
  }
}
