/*
 * Copyright Hyperledger Besu contributors.
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

package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Quantity;

import java.util.List;

public class InclusionList {
  private final List<Inclusion> summary;

  private final Quantity slot;

  private final Quantity proposerIndex;

  private final Hash parentBlockHash;

  public InclusionList(
      final List<Inclusion> summary,
      final Quantity slot,
      final Quantity proposerIndex,
      final Hash parentBlockHash) {
    this.summary = summary;
    this.slot = slot;
    this.proposerIndex = proposerIndex;
    this.parentBlockHash = parentBlockHash;
  }

  public List<Inclusion> getSummary() {
    return summary;
  }

  public Quantity getSlot() {
    return slot;
  }

  public Quantity getProposerIndex() {
    return proposerIndex;
  }

  public Hash getParentBlockHash() {
    return parentBlockHash;
  }

  @Override
  public String toString() {
    return "InclusionList{"
        + "summary="
        + summary
        + ", slot="
        + slot
        + ", proposerIndex="
        + proposerIndex
        + ", parentBlockHash="
        + parentBlockHash
        + '}';
  }
}
