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
package org.hyperledger.besu.ethereum.permissioning;

import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.Blockchain;

import java.util.concurrent.atomic.AtomicLong;

import com.google.common.annotations.VisibleForTesting;

public class QuorumQip714Gate {

  private final long qip714Block;
  private final AtomicLong latestBlock = new AtomicLong(0L);

  public QuorumQip714Gate(final long qip714Block, final Blockchain blockchain) {
    this.qip714Block = qip714Block;

    blockchain.observeBlockAdded(this::checkChainHeight);
  }

  public boolean shouldCheckPermissions() {
    if (qip714Block == 0) {
      return true;
    } else {
      return latestBlock.get() >= qip714Block;
    }
  }

  @VisibleForTesting
  void checkChainHeight(final BlockAddedEvent event) {
    if (event.isNewCanonicalHead()) {
      latestBlock.set(event.getBlock().getHeader().getNumber());
    }
  }

  @VisibleForTesting
  long getLatestBlock() {
    return latestBlock.get();
  }
}
