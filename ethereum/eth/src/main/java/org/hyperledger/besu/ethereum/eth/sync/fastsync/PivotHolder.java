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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Objects;
import javax.annotation.Nonnull;

public class PivotHolder implements PivotProvider {

  private BlockHeader pivotBlockHeader;

  public PivotHolder(final BlockHeader pivotBlockHeader) {
    this.pivotBlockHeader = pivotBlockHeader;
  }

  protected PivotHolder(final PivotHolder pivotHolder) {
    this.pivotBlockHeader = pivotHolder.pivotBlockHeader;
  }

  public long getPivotBlockNumber() {
    return pivotBlockHeader.getNumber();
  }

  @Nonnull
  public BlockHeader getPivotBlockHeader() {
    return pivotBlockHeader;
  }

  public void setCurrentHeader(final BlockHeader header) {
    pivotBlockHeader = header;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PivotHolder that = (PivotHolder) o;
    return Objects.equals(pivotBlockHeader, that.pivotBlockHeader);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pivotBlockHeader);
  }

  @Override
  public String toString() {
    return "FastSyncState{" + ", pivotBlockHeader=" + pivotBlockHeader + '}';
  }

  public PivotBlockProposal toProposal() {
    return new PivotBlockProposal(pivotBlockHeader);
  }

  @Override
  public BlockHeader providePivot() {
    return pivotBlockHeader;
  }
}
