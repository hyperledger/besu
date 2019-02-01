/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.ibft;

import static java.util.Collections.emptyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import tech.pegasys.pantheon.consensus.ibft.network.ValidatorMulticaster;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.AddressHelpers;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.RawMessage;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UniqueMessageMulticasterTest {

  private final ValidatorMulticaster multicaster = mock(ValidatorMulticaster.class);
  private final UniqueMessageMulticaster messageTracker =
      new UniqueMessageMulticaster(multicaster, 5);
  private final RawMessage messageSent = new RawMessage(5, BytesValue.wrap(new byte[5]));

  @Test
  public void previouslySentMessageIsNotSentAgain() {

    messageTracker.send(messageSent);
    verify(multicaster, times(1)).send(messageSent, emptyList());
    reset(multicaster);

    messageTracker.send(messageSent);
    messageTracker.send(messageSent, emptyList());
    verifyZeroInteractions(multicaster);
  }

  @Test
  public void messagesSentWithABlackListAreNotRetransmitted() {
    messageTracker.send(messageSent, emptyList());
    verify(multicaster, times(1)).send(messageSent, emptyList());
    reset(multicaster);

    messageTracker.send(messageSent, emptyList());
    messageTracker.send(messageSent);
    verifyZeroInteractions(multicaster);
  }

  @Test
  public void oldMessagesAreEvictedWhenFullAndCanThenBeRetransmitted() {
    final List<MessageData> messagesSent = Lists.newArrayList();

    for (int i = 0; i < 6; i++) {
      final RawMessage msg = new RawMessage(i, BytesValue.wrap(new byte[i]));
      messagesSent.add(msg);
      messageTracker.send(msg);
      verify(multicaster, times(1)).send(msg, emptyList());
    }
    reset(multicaster);

    messageTracker.send(messagesSent.get(5));
    verifyZeroInteractions(multicaster);

    messageTracker.send(messagesSent.get(0));
    verify(multicaster, times(1)).send(messagesSent.get(0), emptyList());
  }

  @Test
  public void passedInBlackListIsPassedToUnderlyingValidator() {
    final List<Address> blackList =
        Lists.newArrayList(AddressHelpers.ofValue(0), AddressHelpers.ofValue(1));
    messageTracker.send(messageSent, blackList);
    verify(multicaster, times(1)).send(messageSent, blackList);
  }

  @Test
  public void anonymousMessageDataClassesContainingTheSameDataAreConsideredIdentical() {

    final MessageData arbitraryMessage_1 =
        createAnonymousMessageData(BytesValue.wrap(new byte[4]), 1);

    final MessageData arbitraryMessage_2 =
        createAnonymousMessageData(BytesValue.wrap(new byte[4]), 1);

    messageTracker.send(arbitraryMessage_1);
    verify(multicaster, times(1)).send(arbitraryMessage_1, emptyList());
    reset(multicaster);

    messageTracker.send(arbitraryMessage_2);
    verifyZeroInteractions(multicaster);
  }

  private MessageData createAnonymousMessageData(final BytesValue content, final int code) {
    return new MessageData() {

      @Override
      public int getSize() {
        return content.size();
      }

      @Override
      public int getCode() {
        return code;
      }

      @Override
      public BytesValue getData() {
        return content;
      }
    };
  }
}
