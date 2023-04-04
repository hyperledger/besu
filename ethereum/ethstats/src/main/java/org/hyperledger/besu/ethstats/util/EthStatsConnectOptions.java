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
package org.hyperledger.besu.ethstats.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import org.immutables.value.Value;
import org.slf4j.LoggerFactory;

@Value.Immutable
public interface EthStatsConnectOptions {

  Pattern NETSTATS_URL_REGEX = Pattern.compile("([-\\w]+):([-\\w]+)?@([-.\\w]+)(:([\\d]+))?");

  String getNodeName();

  String getSecret();

  String getHost();

  Integer getPort();

  String getContact();

  @Nullable
  Path getCaCert();

  static EthStatsConnectOptions fromParams(
      final String url, final String contact, final Path caCert) {
    try {
      checkArgument(url != null && !url.trim().isEmpty(), "Invalid empty value.");

      final Matcher netStatsUrl = NETSTATS_URL_REGEX.matcher(url);
      if (netStatsUrl.matches()) {
        return ImmutableEthStatsConnectOptions.builder()
            .nodeName(netStatsUrl.group(1))
            .secret(netStatsUrl.group(2))
            .host(netStatsUrl.group(3))
            .port(Integer.parseInt(Optional.ofNullable(netStatsUrl.group(5)).orElse("-1")))
            .contact(contact)
            .caCert(caCert)
            .build();
      }

    } catch (IllegalArgumentException e) {
      LoggerFactory.getLogger(EthStatsConnectOptions.class).error(e.getMessage());
    }
    throw new IllegalArgumentException(
        "Invalid netstats URL syntax. Netstats URL should have the following format 'nodename:secret@host:port' or 'nodename:secret@host'.");
  }
}
