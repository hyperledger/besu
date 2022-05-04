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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.immutables.value.Value;
import org.slf4j.LoggerFactory;

@Value.Immutable
public interface NetstatsUrl {

  Pattern NETSTATS_URL_REGEX = Pattern.compile("([-\\w]+):([\\w]+)?@([-.\\w]+)(:([\\d]+))?");

  String getNodeName();

  String getSecret();

  String getHost();

  Integer getPort();

  String getContact();

  static NetstatsUrl fromParams(final String url, final String contact) {
    try {
      checkArgument(url != null && !url.trim().isEmpty(), "Invalid empty value.");

      final Matcher netStatsUrl = NETSTATS_URL_REGEX.matcher(url);
      if (netStatsUrl.matches()) {
        return ImmutableNetstatsUrl.builder()
            .nodeName(netStatsUrl.group(1))
            .secret(netStatsUrl.group(2))
            .host(netStatsUrl.group(3))
            .port(Integer.parseInt(Optional.ofNullable(netStatsUrl.group(5)).orElse("3000")))
            .contact(contact)
            .build();
      }

    } catch (IllegalArgumentException e) {
      LoggerFactory.getLogger(NetstatsUrl.class).error(e.getMessage());
    }
    throw new IllegalArgumentException(
        "Invalid netstats URL syntax. Netstats URL should have the following format 'nodename:secret@host:port' or 'nodename:secret@host'.");
  }
}
