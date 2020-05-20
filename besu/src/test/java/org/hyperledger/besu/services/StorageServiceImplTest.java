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
package org.hyperledger.besu.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import org.junit.Test;

public class StorageServiceImplTest {

  @Test
  public void assertThatSegmentIsAdded() {
    final StorageServiceImpl storageService = new StorageServiceImpl();
    final SegmentIdentifier segmentIdentifier =
        createSegmentIdentifier(
            "test-segment", new byte[] {(byte) (KeyValueSegmentIdentifier.values().length + 1)});
    assertThat(storageService.getAllSegmentIdentifiers())
        .hasSize(KeyValueSegmentIdentifier.values().length)
        .doesNotContain(segmentIdentifier);
    assertThat(storageService.addSegmentIdentifier(segmentIdentifier)).isTrue();
    assertThat(storageService.getAllSegmentIdentifiers())
        .hasSize(KeyValueSegmentIdentifier.values().length + 1)
        .contains(segmentIdentifier);
  }

  @Test
  public void assertThatSegmentIsAddedOnlyOnce() {
    final StorageServiceImpl storageService = new StorageServiceImpl();
    final SegmentIdentifier segmentIdentifier =
        createSegmentIdentifier(
            "test-segment", new byte[] {(byte) (KeyValueSegmentIdentifier.values().length + 1)});
    assertThat(storageService.addSegmentIdentifier(segmentIdentifier)).isTrue();
    assertThat(storageService.addSegmentIdentifier(segmentIdentifier)).isFalse();
    assertThat(storageService.getAllSegmentIdentifiers())
        .hasSize(KeyValueSegmentIdentifier.values().length + 1)
        .contains(segmentIdentifier);
  }

  private static SegmentIdentifier createSegmentIdentifier(final String name, final byte[] id) {
    return new SegmentIdentifier() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public byte[] getId() {
        return id;
      }
    };
  }
}
