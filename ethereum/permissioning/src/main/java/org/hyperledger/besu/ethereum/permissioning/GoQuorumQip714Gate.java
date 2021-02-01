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

public class GoQuorumQip714Gate {

  private static GoQuorumQip714Gate SINGLE_INSTANCE = null;

  private final long qip714Block;
  private final AtomicLong latestBlock = new AtomicLong(0L);

  @VisibleForTesting
  GoQuorumQip714Gate(final long qip714Block, final Blockchain blockchain) {
    this.qip714Block = qip714Block;

    blockchain.observeBlockAdded(this::checkChainHeight);
  }

  // this is only called during start-up, synchronized access won't hurt performance
  public static synchronized GoQuorumQip714Gate getInstance(
      final long qip714Block, final Blockchain blockchain) {
    if (SINGLE_INSTANCE == null) {
      SINGLE_INSTANCE = new GoQuorumQip714Gate(qip714Block, blockchain);
    } else {
      if (SINGLE_INSTANCE.qip714Block != qip714Block) {
        throw new IllegalStateException(
            "Tried to create Quorum QIP-714 gate with different block config from already instantiated gate block config");
      }
    }

    return SINGLE_INSTANCE;
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
