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
package org.hyperledger.besu.ethereum.eth.ethstats;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EthStatsParameters {

  private static final Pattern ETHSTATS_VALID = Pattern.compile("(\\w+):(\\w+)@(\\w+):(\\d+)");

  private final String node;
  private final String secret;
  private final String host;
  private final int port;

  public EthStatsParameters(final String value) {
    Matcher matcher = ETHSTATS_VALID.matcher(value);
    if (matcher.matches()) {
      node = matcher.group(1);
      secret = matcher.group(2);
      host = matcher.group(3);
      port = Integer.parseInt(matcher.group(4));
    } else {
      node = secret = host = null;
      port = 0;
    }
  }

  public boolean isValid() {
    return node != null;
  }

  public String getNode() {
    return node;
  }

  public String getSecret() {
    return secret;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  @Override
  public String toString() {
    return node + ":" + secret + "@" + host + ":" + port;
  }
}
