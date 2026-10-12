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
package org.hyperledger.besu.ethereum.eth.sync.snapsync.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.v2.SnapV2WorldDownloadState.computeChildQueueHighWatermark;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.v2.SnapV2WorldDownloadState.evaluateAccountBackpressure;

import org.junit.jupiter.api.Test;

class SnapV2AccountBackpressureTest {

  private static final long MIB = 1L << 20;
  private static final long GIB = 1L << 30;
  private static final long LOW_WATERMARK = 30_720;

  @Test
  void highWatermarkIsSizedFromTheHeapBudget() {
    // 5% of 16 GiB at 1 KiB per queued request
    assertThat(computeChildQueueHighWatermark(LOW_WATERMARK, 16 * GIB)).isEqualTo(838_860);
  }

  @Test
  void smallHeapKeepsTheCountFloor() {
    assertThat(computeChildQueueHighWatermark(LOW_WATERMARK, 256 * MIB))
        .isEqualTo(2 * LOW_WATERMARK);
  }

  @Test
  void unknownHeapLimitKeepsTheCountFloor() {
    assertThat(computeChildQueueHighWatermark(LOW_WATERMARK, Long.MAX_VALUE))
        .isEqualTo(2 * LOW_WATERMARK);
  }

  @Test
  void pausesAtTheHighWatermarkAndResumesBelowTheLowWatermark() {
    assertThat(evaluateAccountBackpressure(false, 100, 200, 199)).isFalse();
    assertThat(evaluateAccountBackpressure(false, 100, 200, 200)).isTrue();
    assertThat(evaluateAccountBackpressure(true, 100, 200, 150)).isTrue();
    assertThat(evaluateAccountBackpressure(true, 100, 200, 99)).isFalse();
  }
}
