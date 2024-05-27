/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.p2p.discovery.dns;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.google.common.base.Splitter;
import org.junit.jupiter.api.Test;

public class DnsPortSplitTest {
  @Test
  void hostWithoutPortShouldBeParsed() {
    final String host = "localhost";
    final List<String> hostPort = Splitter.on(":").splitToList(host);
    assertThat(hostPort).hasSize(1).containsExactly("localhost");
  }

  @Test
  void hostWithPortShouldBeParsed() {
    final String host = "localhost:52";
    final List<String> hostPort = Splitter.on(":").splitToList(host);
    assertThat(hostPort).hasSize(2).containsExactly("localhost", "52");
  }
}
