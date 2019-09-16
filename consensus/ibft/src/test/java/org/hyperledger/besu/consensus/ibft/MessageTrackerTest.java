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
package org.hyperledger.besu.consensus.ibft;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.util.bytes.BytesValue;

import org.junit.Test;

public class MessageTrackerTest {
  private final MessageTracker messageTracker = new MessageTracker(5);

  @Test
  public void duplicateMessagesAreConsideredSeen() {
    final MessageData arbitraryMessage_1 =
        createAnonymousMessageData(BytesValue.wrap(new byte[4]), 1);

    final MessageData arbitraryMessage_2 =
        createAnonymousMessageData(BytesValue.wrap(new byte[4]), 1);

    assertThat(messageTracker.hasSeenMessage(arbitraryMessage_1)).isFalse();
    assertThat(messageTracker.hasSeenMessage(arbitraryMessage_2)).isFalse();

    messageTracker.addSeenMessage(arbitraryMessage_1);
    assertThat(messageTracker.hasSeenMessage(arbitraryMessage_2)).isTrue();
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
