/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.datatypes.Hash;

import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FinalizedBlockHashSupplier
    implements Supplier<Optional<Hash>>, ForkchoiceMessageListener {
  private static final Logger LOG = LoggerFactory.getLogger(FinalizedBlockHashSupplier.class);

  private volatile Optional<Hash> lastAnnouncedFinalizedBlockHash = Optional.empty();

  @Override
  public void onNewForkchoiceMessage(
      final Hash headBlockHash,
      final Optional<Hash> maybeFinalizedBlockHash,
      final Hash safeBlockHash) {
    lastAnnouncedFinalizedBlockHash = maybeFinalizedBlockHash;
    LOG.debug("New finalized block hash announced {}", lastAnnouncedFinalizedBlockHash);
  }

  @Override
  public Optional<Hash> get() {
    return lastAnnouncedFinalizedBlockHash;
  }
}
