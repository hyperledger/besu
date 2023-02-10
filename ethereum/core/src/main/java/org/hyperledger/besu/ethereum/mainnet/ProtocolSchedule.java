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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;

import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class ProtocolSchedule
    implements HeaderBasedProtocolSchedule, PrivacySupportingProtocolSchedule {

  private Supplier<Boolean> isPostMerge = () -> false;

  public abstract ProtocolSpec getByBlockNumber(long number);

  public abstract Stream<Long> streamMilestoneBlocks();

  public void setIsPostMerge(final Supplier<Boolean> isPostMerge) {
    this.isPostMerge = isPostMerge;
  }

  public boolean isPostMerge() {
    return isPostMerge.get();
  }

  @Override
  public ProtocolSpec getByBlockHeader(final ProcessableBlockHeader blockHeader) {
    return getByBlockNumber(blockHeader.getNumber());
  }
}
