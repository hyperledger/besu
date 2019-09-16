/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.cli.options;

import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;

import java.util.Arrays;
import java.util.List;

import picocli.CommandLine;

public class TransactionPoolOptions implements CLIOptions<TransactionPoolConfiguration.Builder> {
  private static final String TX_MESSAGE_KEEP_ALIVE_SEC_FLAG =
      "--Xincoming-tx-messages-keep-alive-seconds";

  @CommandLine.Option(
      names = {TX_MESSAGE_KEEP_ALIVE_SEC_FLAG},
      paramLabel = "<INTEGER>",
      hidden = true,
      description =
          "Keep alive of incoming transaction messages in seconds (default: ${DEFAULT-VALUE})",
      arity = "1")
  private Integer txMessageKeepAliveSeconds =
      TransactionPoolConfiguration.DEFAULT_TX_MSG_KEEP_ALIVE;

  private TransactionPoolOptions() {}

  public static TransactionPoolOptions create() {
    return new TransactionPoolOptions();
  }

  public static TransactionPoolOptions fromConfig(final TransactionPoolConfiguration config) {
    final TransactionPoolOptions options = TransactionPoolOptions.create();
    options.txMessageKeepAliveSeconds = config.getTxMessageKeepAliveSeconds();
    return options;
  }

  @Override
  public TransactionPoolConfiguration.Builder toDomainObject() {
    return TransactionPoolConfiguration.builder()
        .txMessageKeepAliveSeconds(txMessageKeepAliveSeconds);
  }

  @Override
  public List<String> getCLIOptions() {
    return Arrays.asList(
        TX_MESSAGE_KEEP_ALIVE_SEC_FLAG, OptionParser.format(txMessageKeepAliveSeconds));
  }
}
