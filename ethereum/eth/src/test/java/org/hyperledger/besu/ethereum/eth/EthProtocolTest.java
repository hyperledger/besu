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
package org.hyperledger.besu.ethereum.eth;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;

import org.junit.Test;

public class EthProtocolTest {

  @Test
  public void eth66CheckShouldReturnTrueForCompatibleProtocols() {
    assertThat(EthProtocol.isEth66Compatible(EthProtocol.ETH66)).isTrue();
  }

  @Test
  public void eth66CheckShouldReturnFalseForIncompatibleProtocols() {
    assertThat(EthProtocol.isEth66Compatible(EthProtocol.ETH62)).isFalse();
    assertThat(EthProtocol.isEth66Compatible(EthProtocol.ETH63)).isFalse();
    assertThat(EthProtocol.isEth66Compatible(EthProtocol.ETH64)).isFalse();
    assertThat(EthProtocol.isEth66Compatible(EthProtocol.ETH65)).isFalse();

    assertThat(EthProtocol.isEth66Compatible(Capability.create("IBF", 1))).isFalse();
    assertThat(EthProtocol.isEth66Compatible(Capability.create("istanbul", 66))).isFalse();
    assertThat(EthProtocol.isEth66Compatible(Capability.create("istanbul", 100))).isFalse();
  }

  @Test
  public void eth66CheckWithNullNameReturnsFalse() {
    assertThat(EthProtocol.isEth66Compatible(Capability.create(null, 1))).isFalse();
  }
}
