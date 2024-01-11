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

import java.net.URI;
import java.nio.file.Path;
import javax.annotation.Nullable;

import org.immutables.value.Value;
import org.slf4j.LoggerFactory;

@Value.Immutable
public interface EthStatsConnectOptions {
  @Nullable
  String getScheme();

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

      // if scheme is not specified in the URI, user info (nodename) gets converted to scheme.
      final URI uri;
      final String scheme;
      if (url.matches("^.*://.*$")) {
        // construct URI
        uri = URI.create(url);
        scheme = uri.getScheme();
      } else {
        // prepend ws:// to make a valid URI while keeping scheme as null
        uri = URI.create("ws://" + url);
        scheme = null;
      }

      if (scheme != null) {
        // make sure that scheme is either ws or wss
        if (!scheme.equalsIgnoreCase("ws") && !scheme.equalsIgnoreCase("wss")) {
          throw new IllegalArgumentException("Ethstats URI only support ws:// or wss:// scheme.");
        }
      }

      final String userInfo = uri.getUserInfo();

      // make sure user info is specified
      if (userInfo == null || !userInfo.contains(":")) {
        throw new IllegalArgumentException("Ethstats URI missing user info.");
      }
      final String nodeName = userInfo.substring(0, userInfo.indexOf(":"));
      final String secret = userInfo.substring(userInfo.indexOf(":") + 1);

      return ImmutableEthStatsConnectOptions.builder()
          .scheme(scheme)
          .nodeName(nodeName)
          .secret(secret)
          .host(uri.getHost())
          .port(uri.getPort())
          .contact(contact)
          .caCert(caCert)
          .build();
    } catch (IllegalArgumentException e) {
      LoggerFactory.getLogger(EthStatsConnectOptions.class).error(e.getMessage());
    }
    throw new IllegalArgumentException(
        "Invalid ethstats URL syntax. Ethstats URL should have the following format '[ws://|wss://]nodename:secret@host[:port]'.");
  }
}
