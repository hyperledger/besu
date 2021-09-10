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
package org.hyperledger.besu.ethereum.eth.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.messages.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetAccountRangeMessage;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class SnapServerTest {

  private static final Bytes VALUE1 = Bytes.of(1);
  private static final Bytes VALUE2 = Bytes.of(2);
  private static final Bytes VALUE3 = Bytes.of(3);
  private static final Hash STATEROOT = Hash.hash(VALUE1);
  private static final Hash START_HASH = Hash.hash(VALUE2);
  private static final Hash END_HASH = Hash.hash(VALUE3);
  private final EthPeer ethPeer = mock(EthPeer.class);
  private final EthMessages ethMessages = new EthMessages();

  @Before
  public void setUp() {
    new SnapServer(ethMessages);
  }

  @Test
  public void shouldHandleGetAccountRangeRequests() throws Exception {
    assertThat(
            ethMessages.dispatch(
                new EthMessage(
                    ethPeer, GetAccountRangeMessage.create(STATEROOT, START_HASH, END_HASH))))
        .contains(AccountRangeMessage.create());
  }
}
