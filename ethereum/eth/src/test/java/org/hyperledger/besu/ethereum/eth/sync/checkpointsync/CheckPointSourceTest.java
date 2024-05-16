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
package org.hyperledger.besu.ethereum.eth.sync.checkpointsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CheckPointSourceTest {

  private final SyncState syncState = mock(SyncState.class);
  private CheckpointSource checkPointSource;

  @BeforeEach
  public void setup() {
    checkPointSource = new CheckpointSource(syncState, header(12), 1);
  }

  @Test
  public void shouldNotHasNextWhenChainHeightIsNotZero() {
    when(syncState.getLocalChainHeight()).thenReturn(1L);
    assertThat(checkPointSource.hasNext()).isFalse();
  }

  @Test
  public void shouldHasNextWhenLocalChainIsZero() {
    when(syncState.getLocalChainHeight()).thenReturn(0L);
    assertThat(checkPointSource.hasNext()).isTrue();
  }

  @Test
  public void shouldHasNextWhenMissingHeader() {
    when(syncState.getLocalChainHeight()).thenReturn(0L);
    checkPointSource.setLastHeaderDownloaded(Optional.of(header(12)));
    assertThat(checkPointSource.hasNext()).isTrue();
  }

  @Test
  public void shouldReturnCheckPointForFirstNext() {
    assertThat(checkPointSource.next()).isEqualTo(checkPointSource.getCheckpoint().getHash());
  }

  @Test
  public void shouldReturnParentHashForOtherNextCall() {
    final BlockHeader header = header(12);
    checkPointSource.setLastHeaderDownloaded(Optional.of(header));
    assertThat(checkPointSource.next()).isEqualTo(header.getParentHash());
  }

  private BlockHeader header(final int number) {
    return new BlockHeaderTestFixture().number(number).buildHeader();
  }
}
