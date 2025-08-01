/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.services;

import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.services.WorldStateService;

/**
 * Implementation of the {@link WorldStateService} that provides access to Besu's world state.
 *
 * <p>This implementation delegates world state operations to the underlying {@link
 * WorldStateArchive}.
 */
@Unstable
public class WorldStateServiceImpl implements WorldStateService {
  private final WorldStateArchive worldStateArchive;

  /**
   * Constructs a new WorldStateServiceImpl.
   *
   * @param worldStateArchive The world state archive that provides access to world state data
   */
  public WorldStateServiceImpl(final WorldStateArchive worldStateArchive) {
    this.worldStateArchive = worldStateArchive;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns a view of the current world state by delegating to the underlying world state
   * archive.
   *
   * @return A view of the current world state
   */
  @Override
  public WorldView getWorldView() {
    return worldStateArchive.getWorldState();
  }
}
