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
package org.hyperledger.besu.consensus.ibft;

import org.hyperledger.besu.consensus.ibft.ibftevent.NewChainHead;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.Blockchain;

/**
 * Blockchain observer that adds {@link NewChainHead} events to the event queue when a new block is
 * added to the chain head
 */
public class IbftChainObserver implements BlockAddedObserver {
  private final IbftEventQueue queue;

  public IbftChainObserver(final IbftEventQueue queue) {
    this.queue = queue;
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event, final Blockchain blockchain) {
    switch (event.getEventType()) {
      case HEAD_ADVANCED:
        queue.add(new NewChainHead(event.getBlock().getHeader()));
        break;

      default:
        throw new IllegalStateException(
            String.format("Unexpected BlockAddedEvent received: %s", event.getEventType()));
    }
  }
}
