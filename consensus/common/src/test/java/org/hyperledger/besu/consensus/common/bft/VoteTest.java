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
package org.hyperledger.besu.consensus.common.bft;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;

import org.junit.jupiter.api.Test;

public class VoteTest {
  @Test
  public void testStaticVoteCreationMethods() {
    assertThat(Vote.authVote(Address.fromHexString("1")).isAuth()).isEqualTo(true);
    assertThat(Vote.authVote(Address.fromHexString("1")).isDrop()).isEqualTo(false);

    assertThat(Vote.dropVote(Address.fromHexString("1")).isAuth()).isEqualTo(false);
    assertThat(Vote.dropVote(Address.fromHexString("1")).isDrop()).isEqualTo(true);
  }
}
