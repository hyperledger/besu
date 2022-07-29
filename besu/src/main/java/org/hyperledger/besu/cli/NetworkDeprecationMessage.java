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
package org.hyperledger.besu.cli;

import org.hyperledger.besu.cli.config.NetworkName;

import org.apache.commons.lang3.StringUtils;

public class NetworkDeprecationMessage {
  private static final int MAX_LINE_LENGTH = 80;

  public static String generate(final NetworkName network) {
    if (network.getDeprecationDate().isEmpty()) {
      throw new AssertionError("Deprecation date is not set. Cannot print a deprecation message");
    }

    final StringBuilder messageBuilder = new StringBuilder("\n");
    messageBuilder
        .append("#".repeat(MAX_LINE_LENGTH))
        .append(emptyLine())
        .append(
            String.format(
                "#%s#",
                StringUtils.center(
                    deprecationDetails(
                        network.humanReadableNetworkName(), network.getDeprecationDate().get()),
                    MAX_LINE_LENGTH - 2)))
        .append(emptyLine())
        .append(
            String.format(
                "#%s#\n", StringUtils.center("For more details please go to", MAX_LINE_LENGTH - 2)))
        .append(
            String.format(
                "#%s#",
                StringUtils.center(
                    "https://blog.ethereum.org/2022/06/21/testnet-deprecation/",
                    MAX_LINE_LENGTH - 2)))
        .append(emptyLine())
        .append("#".repeat(MAX_LINE_LENGTH));

    return messageBuilder.toString();
  }

  private static String deprecationDetails(final String networkName, final String deprecationDate) {
    return networkName + " is deprecated and will be shutdown " + deprecationDate;
  }

  private static String emptyLine() {
    return String.format("\n#%s#\n", StringUtils.center("", MAX_LINE_LENGTH - 2));
  }
}
