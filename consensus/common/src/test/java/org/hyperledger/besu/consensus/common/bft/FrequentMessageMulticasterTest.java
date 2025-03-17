/*
 * Copyright contributors to Besu.
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

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.consensus.common.bft.network.ValidatorMulticaster;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FrequentMessageMulticasterTest {
  private FrequentMessageMulticaster multicaster;
  private ValidatorMulticaster mockValidatorMulticaster;

  @BeforeEach
  void setUp() {
    mockValidatorMulticaster = mock(ValidatorMulticaster.class);
    multicaster = new FrequentMessageMulticaster(mockValidatorMulticaster, 5000);
  }

  @AfterEach
  void tearDown() {
    multicaster.stopFrequentMulticasting();
  }

  @Test
  void testMessageIsSentPeriodically() throws InterruptedException {
    MessageData message = mock(MessageData.class);

    multicaster.send(message);

    // Allow time for multiple executions
    TimeUnit.MILLISECONDS.sleep(12000);

    // The message should have been sent multiple times
    verify(mockValidatorMulticaster, atLeast(2)).send(message, Collections.emptyList());
  }

  @Test
  void testNewMessageReplacesOldTask() throws InterruptedException {
    MessageData message1 = mock(MessageData.class);
    MessageData message2 = mock(MessageData.class);

    multicaster.send(message1);
    TimeUnit.MILLISECONDS.sleep(2000); // Allow initial message to start

    multicaster.send(message2); // This should replace the old task

    // Wait and check if message1 was stopped
    TimeUnit.MILLISECONDS.sleep(7000);

    verify(mockValidatorMulticaster, atMost(2)).send(message1, Collections.emptyList());
    verify(mockValidatorMulticaster, atLeast(1)).send(message2, Collections.emptyList());
  }

  @Test
  void testCancelStopsFrequentMulticasting() throws InterruptedException {
    MessageData message = mock(MessageData.class);

    multicaster.send(message);
    TimeUnit.MILLISECONDS.sleep(7000);

    multicaster.stopFrequentMulticasting();

    TimeUnit.MILLISECONDS.sleep(7000);

    verify(mockValidatorMulticaster, atMost(3)).send(message, Collections.emptyList());
  }
}
