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
package org.hyperledger.besu.util.e2;

import java.util.List;

public class E2SlotIndex {
  private final long startingSlot;
  private final List<Long> indexes;

  public E2SlotIndex(final long startingSlot, final List<Long> indexes) {
    this.startingSlot = startingSlot;
    this.indexes = indexes;
  }

  public long getStartingSlot() {
    return startingSlot;
  }

  public List<Long> getIndexes() {
    return indexes;
  }
}
