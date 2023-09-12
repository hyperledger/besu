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
package org.hyperledger.besu.cli.options.unstable;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.cli.converter.DurationMillisConverter;
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
    internalTestSuccess(
        config ->
            assertThat(config.getTxMessageKeepAliveSeconds()).isEqualTo(txMessageKeepAliveSeconds),
        "--Xincoming-tx-messages-keep-alive-seconds",
        String.valueOf(txMessageKeepAliveSeconds));
  }

  @Test
  public void txMessageKeepAliveSecondsWithInvalidInputShouldFail() {
    internalTestFailure(
        "Invalid value for option '--Xincoming-tx-messages-keep-alive-seconds': 'acbd' is not an int",
        "--Xincoming-tx-messages-keep-alive-seconds",
        "acbd");
  }

  @Test
  public void eth65TrxAnnouncedBufferingPeriod() {
    final Duration eth65TrxAnnouncedBufferingPeriod = Duration.ofMillis(999);
    internalTestSuccess(
        config ->
            assertThat(config.getEth65TrxAnnouncedBufferingPeriod())
                .isEqualTo(eth65TrxAnnouncedBufferingPeriod),
        "--Xeth65-tx-announced-buffering-period-milliseconds",
        new DurationMillisConverter().format(eth65TrxAnnouncedBufferingPeriod));
  }

  @Test
  public void eth65TrxAnnouncedBufferingPeriodWithInvalidInputShouldFail() {
    internalTestFailure(
        "Invalid value for option '--Xeth65-tx-announced-buffering-period-milliseconds': cannot convert 'acbd' to Duration (org.hyperledger.besu.cli.converter.exception.DurationConversionException: 'acbd' is not a long)",
        "--Xeth65-tx-announced-buffering-period-milliseconds",
        "acbd");
  }

  @Test
  public void eth65TrxAnnouncedBufferingPeriodWithInvalidInputShouldFail2() {
    internalTestFailure(
        "Invalid value for option '--Xeth65-tx-announced-buffering-period-milliseconds': cannot convert '-1' to Duration (org.hyperledger.besu.cli.converter.exception.DurationConversionException: negative value '-1' is not allowed)",
        "--Xeth65-tx-announced-buffering-period-milliseconds",
        "-1");
  }

  @Override
  protected TransactionPoolConfiguration.Unstable createDefaultDomainObject() {
    return TransactionPoolConfiguration.Unstable.DEFAULT;
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
