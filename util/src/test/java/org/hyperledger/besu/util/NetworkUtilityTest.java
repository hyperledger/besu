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
package org.hyperledger.besu.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;

import org.junit.Test;

public class NetworkUtilityTest {

  @Test
  public void urlForSocketAddressHandlesIPv6() {
    final InetSocketAddress ipv6All = new InetSocketAddress("::", 80);
    assertThat(NetworkUtility.urlForSocketAddress("http", ipv6All)).doesNotContain("::");
    assertThat(NetworkUtility.urlForSocketAddress("http", ipv6All))
        .doesNotContain("0:0:0:0:0:0:0:0");
    final InetSocketAddress ipv6 = new InetSocketAddress("1:2:3:4:5:6:7:8", 80);
    assertThat(NetworkUtility.urlForSocketAddress("http", ipv6)).contains("[1:2:3:4:5:6:7:8]");
  }
}
