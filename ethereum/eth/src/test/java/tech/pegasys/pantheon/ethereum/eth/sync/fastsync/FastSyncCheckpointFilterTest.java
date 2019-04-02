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
package tech.pegasys.pantheon.ethereum.eth.sync.fastsync;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;

import java.util.List;

import org.junit.Test;

public class FastSyncCheckpointFilterTest {
  private final BlockHeader pivotBlockHeader = header(50);
  private final FastSyncCheckpointFilter filter = new FastSyncCheckpointFilter(pivotBlockHeader);

  @Test
  public void shouldNotChangeCheckpointsPriorToThePivotBlock() {
    final List<BlockHeader> input =
        asList(header(10), header(20), header(30), header(40), header(49));

    assertThat(filter.apply(input)).isEqualTo(input);
  }

  @Test
  public void shouldRemoveCheckpointsBeyondPivotBlock() {
    final List<BlockHeader> input = asList(header(40), header(50), header(60), header(70));
    assertThat(filter.apply(input)).containsExactly(header(40), header(50));
  }

  @Test
  public void shouldAppendPivotBlockHeaderWhenRemovingCheckpointsIfNotAlreadyPresent() {
    final List<BlockHeader> input = asList(header(45), header(55), header(65));
    assertThat(filter.apply(input)).containsExactly(header(45), header(50));
  }

  @Test
  public void shouldReturnOnlyPivotBlockHeaderIfAllBlocksAreAfterPivotBlock() {
    assertThat(filter.apply(asList(header(55), header(60)))).containsExactly(pivotBlockHeader);
  }

  @Test
  public void shouldNotChangeEmptyHeaders() {
    assertThat(filter.apply(emptyList())).isEmpty();
  }

  private BlockHeader header(final int number) {
    return new BlockHeaderTestFixture().number(number).buildHeader();
  }
}
