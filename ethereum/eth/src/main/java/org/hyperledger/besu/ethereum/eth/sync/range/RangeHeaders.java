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
package org.hyperledger.besu.ethereum.eth.sync.range;

import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.common.base.MoreObjects;

public class RangeHeaders {

  private final SyncTargetRange range;
  private final List<BlockHeader> headersToImport;

  public RangeHeaders(
      final SyncTargetRange checkpointRange, final List<BlockHeader> headersToImport) {
    this.range = checkpointRange;
    this.headersToImport = headersToImport;
  }

  public SyncTargetRange getRange() {
    return range;
  }

  public List<BlockHeader> getHeadersToImport() {
    return headersToImport;
  }

  public Optional<BlockHeader> getFirstHeaderToImport() {
    return headersToImport.size() > 0 ? Optional.of(headersToImport.get(0)) : Optional.empty();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RangeHeaders that = (RangeHeaders) o;
    return Objects.equals(range, that.range)
        && Objects.equals(headersToImport, that.headersToImport);
  }

  @Override
  public int hashCode() {
    return Objects.hash(range, headersToImport);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("range", range)
        .add("headersToImport", headersToImport)
        .toString();
  }
}
