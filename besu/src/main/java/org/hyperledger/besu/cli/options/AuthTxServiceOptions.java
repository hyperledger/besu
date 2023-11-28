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

import org.hyperledger.besu.ethereum.eth.authtxservice.AuthTxServiceConfiguration;

import java.util.Arrays;
import java.util.List;

import picocli.CommandLine;

public class AuthTxServiceOptions implements CLIOptions<AuthTxServiceConfiguration> {
  private static final String AUTH_TRANSACTIONS_ENDPOINT = "--auth-transactions-endpoint";
  private static final String AUTH_TRANSACTIONS_ENABLED = "--auth-transactions-enabled";

  @CommandLine.Option(
      names = {AUTH_TRANSACTIONS_ENABLED},
      description = "Enable the transactions validation service (default: ${DEFAULT-VALUE})")
  @SuppressWarnings({"FieldCanBeFinal"}) // PicoCLI requires non-final Strings.
  private Boolean authTransactionsEnabled = Boolean.FALSE;

  @CommandLine.Option(
      names = {AUTH_TRANSACTIONS_ENDPOINT},
      paramLabel = "<STRING>",
      description =
          "Messaging service endpoint for the transactions validation service."
              + "This feature relays all eth_sendRawTransactions method (except genesis contracts) "
              + "for pre-approval before being loaded into the transaction pool",
      arity = "1")
  @SuppressWarnings({"FieldCanBeFinal"}) // PicoCLI requires non-final Strings.
  private String authTransactionsEndpoint = null;

  private AuthTxServiceOptions() {}

  public static AuthTxServiceOptions create() {
    return new AuthTxServiceOptions();
  }

  public String getAuthTransactionsEndpoint() {
    return this.authTransactionsEndpoint;
  }

  public Boolean getAuthTransactionsEnabled() {
    return this.authTransactionsEnabled;
  }

  @Override
  public AuthTxServiceConfiguration toDomainObject() {
    AuthTxServiceConfiguration config = AuthTxServiceConfiguration.createDefault();
    config.setEndpoint(authTransactionsEndpoint);
    config.setEnabled(authTransactionsEnabled);
    return null;
  }

  @Override
  public List<String> getCLIOptions() {
    return Arrays.asList(
        AUTH_TRANSACTIONS_ENDPOINT,
        authTransactionsEndpoint,
        AUTH_TRANSACTIONS_ENABLED,
        authTransactionsEnabled.toString());
  }
}
