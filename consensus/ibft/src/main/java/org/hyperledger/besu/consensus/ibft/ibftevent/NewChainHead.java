/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.consensus.ibft.ibftevent;

import org.hyperledger.besu.consensus.ibft.ibftevent.IbftEvents.Type;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Objects;

import com.google.common.base.MoreObjects;

/** Event indicating that new chain head has been received */
public final class NewChainHead implements IbftEvent {
  private final BlockHeader newChainHeadHeader;

  /**
   * Constructor for a NewChainHead event
   *
   * @param newChainHeadHeader The header of the current blockchain head
   */
  public NewChainHead(final BlockHeader newChainHeadHeader) {
    this.newChainHeadHeader = newChainHeadHeader;
  }

  @Override
  public Type getType() {
    return Type.NEW_CHAIN_HEAD;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("New Chain Head Header", newChainHeadHeader)
        .toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final NewChainHead that = (NewChainHead) o;
    return Objects.equals(newChainHeadHeader, that.newChainHeadHeader);
  }

  @Override
  public int hashCode() {
    return Objects.hash(newChainHeadHeader);
  }

  public BlockHeader getNewChainHeadHeader() {
    return newChainHeadHeader;
  }
}
