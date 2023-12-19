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

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class MessageTrackerTest {
  private final MessageTracker messageTracker = new MessageTracker(5);

  @Test
  public void duplicateMessagesAreConsideredSeen() {
    final MessageData arbitraryMessage_1 = createAnonymousMessageData(Bytes.wrap(new byte[4]), 1);

    final MessageData arbitraryMessage_2 = createAnonymousMessageData(Bytes.wrap(new byte[4]), 1);

    assertThat(messageTracker.hasSeenMessage(arbitraryMessage_1)).isFalse();
    assertThat(messageTracker.hasSeenMessage(arbitraryMessage_2)).isFalse();

    messageTracker.addSeenMessage(arbitraryMessage_1);
    assertThat(messageTracker.hasSeenMessage(arbitraryMessage_2)).isTrue();
  }

  private MessageData createAnonymousMessageData(final Bytes content, final int code) {
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
      public Bytes getData() {
        return content;
      }
    };
  }
}
