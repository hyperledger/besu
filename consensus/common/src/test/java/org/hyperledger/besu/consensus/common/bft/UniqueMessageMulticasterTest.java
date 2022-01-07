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

import static java.util.Collections.emptyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.network.ValidatorMulticaster;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;

import java.util.List;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UniqueMessageMulticasterTest {

  private final MessageTracker messageTracker = mock(MessageTracker.class);
  private final ValidatorMulticaster multicaster = mock(ValidatorMulticaster.class);
  private final UniqueMessageMulticaster uniqueMessageMulticaster =
      new UniqueMessageMulticaster(multicaster, messageTracker);
  private final RawMessage messageSent = new RawMessage(5, Bytes.wrap(new byte[5]));

  @Test
  public void previouslySentMessageIsNotSentAgain() {
    when(messageTracker.hasSeenMessage(messageSent)).thenReturn(false);
    uniqueMessageMulticaster.send(messageSent);
    verify(multicaster, times(1)).send(messageSent, emptyList());
    reset(multicaster);

    when(messageTracker.hasSeenMessage(messageSent)).thenReturn(true);
    uniqueMessageMulticaster.send(messageSent);
    uniqueMessageMulticaster.send(messageSent, emptyList());
    verifyNoInteractions(multicaster);
  }

  @Test
  public void messagesSentWithADenylistAreNotRetransmitted() {
    when(messageTracker.hasSeenMessage(messageSent)).thenReturn(false);
    uniqueMessageMulticaster.send(messageSent, emptyList());
    verify(multicaster, times(1)).send(messageSent, emptyList());
    reset(multicaster);

    when(messageTracker.hasSeenMessage(messageSent)).thenReturn(true);
    uniqueMessageMulticaster.send(messageSent, emptyList());
    uniqueMessageMulticaster.send(messageSent);
    verifyNoInteractions(multicaster);
  }

  @Test
  public void passedInDenylistIsPassedToUnderlyingValidator() {
    final List<Address> denylist =
        Lists.newArrayList(AddressHelpers.ofValue(0), AddressHelpers.ofValue(1));
    uniqueMessageMulticaster.send(messageSent, denylist);
    verify(multicaster, times(1)).send(messageSent, denylist);
  }
}
