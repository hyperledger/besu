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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class NetstatsUrlTest {

  private final String VALID_NETSTATS_URL = "Dev-Node-1:secret@127.0.0.1:3001";

  private final String CONTACT = "contact@mail.fr";

  private final String ERROR_MESSAGE =
      "Invalid netstats URL syntax. Netstats URL should have the following format 'nodename:secret@host:port' or 'nodename:secret@host'.";

  @Test
  public void buildWithValidParams() {
    final NetstatsUrl netstatsUrl = NetstatsUrl.fromParams(VALID_NETSTATS_URL, CONTACT);
    assertThat(netstatsUrl.getHost()).isEqualTo("127.0.0.1");
    assertThat(netstatsUrl.getNodeName()).isEqualTo("Dev-Node-1");
    assertThat(netstatsUrl.getPort()).isEqualTo(3001);
    assertThat(netstatsUrl.getSecret()).isEqualTo("secret");
    assertThat(netstatsUrl.getContact()).isEqualTo(CONTACT);
  }

  @Test
  public void buildWithValidHost() {
    final String[] validHosts =
        new String[] {"url-test.test.com", "url.test.com", "test.com", "10.10.10.15"};
    for (String host : validHosts) {
      final NetstatsUrl netstatsUrl =
          NetstatsUrl.fromParams("Dev-Node-1:secret@" + host + ":3001", CONTACT);
      assertThat(netstatsUrl.getHost()).isEqualTo(host);
    }
  }

  @Test
  public void buildWithValidHostWithoutPort() {
    final String[] validHosts =
        new String[] {"url-test.test.com", "url.test.com", "test.com", "10.10.10.15"};
    for (String host : validHosts) {
      final NetstatsUrl netstatsUrl = NetstatsUrl.fromParams("Dev-Node-1:secret@" + host, CONTACT);
      assertThat(netstatsUrl.getHost()).isEqualTo(host);
      assertThat(netstatsUrl.getPort()).isEqualTo(3000);
    }
  }

  @Test
  public void shouldDetectEmptyParams() {
    assertThatThrownBy(() -> NetstatsUrl.fromParams("", CONTACT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(ERROR_MESSAGE);
  }

  @Test
  public void shouldDetectMissingParams() {
    // missing node name
    assertThatThrownBy(() -> NetstatsUrl.fromParams("secret@127.0.0.1:3001", CONTACT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(ERROR_MESSAGE);

    // missing host
    assertThatThrownBy(() -> NetstatsUrl.fromParams("Dev-Node-1:secret@", CONTACT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(ERROR_MESSAGE);

    // missing port
    assertThatThrownBy(() -> NetstatsUrl.fromParams("Dev-Node-1:secret@127.0.0.1:", CONTACT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(ERROR_MESSAGE);
  }

  @Test
  public void shouldDetectInvalidParams() {
    // invalid host
    assertThatThrownBy(() -> NetstatsUrl.fromParams("Dev-Node-1:secret@127.0@0.1:3001", CONTACT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(ERROR_MESSAGE);

    // invalid port
    assertThatThrownBy(() -> NetstatsUrl.fromParams("Dev-Node-1:secret@127.0.0.1:A001", CONTACT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(ERROR_MESSAGE);
  }
}
