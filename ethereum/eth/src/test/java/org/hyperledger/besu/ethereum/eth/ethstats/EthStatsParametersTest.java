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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class EthStatsParametersTest {

  @Test
  public void testValidateEthStatsURL() {
    EthStatsParameters params = new EthStatsParameters("nodename:secret@host:12345");
    assertThat(params.isValid()).isTrue();
  }

  @Test
  public void testValidateEthStatsURLInvalid() {
    EthStatsParameters params = new EthStatsParameters("nodename:secret@host:123pp45");
    assertThat(params.isValid()).isFalse();
  }

  @Test
  public void testCheckEthStatsURLElements() {
    EthStatsParameters params = new EthStatsParameters("nodename:secret@host:12345");
    assertThat(params.getNode()).isEqualTo("nodename");
    assertThat(params.getSecret()).isEqualTo("secret");
    assertThat(params.getHost()).isEqualTo("host");
    assertThat(params.getPort()).isEqualTo(12345);
  }
}
