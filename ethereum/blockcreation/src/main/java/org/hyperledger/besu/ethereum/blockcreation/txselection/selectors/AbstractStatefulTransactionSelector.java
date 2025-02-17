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
package org.hyperledger.besu.ethereum.blockcreation.txselection.selectors;

import org.hyperledger.besu.ethereum.blockcreation.txselection.BlockSelectionContext;
import org.hyperledger.besu.plugin.services.txselection.SelectorsStateManager;

/**
 * This class represents an abstract transaction selector which provides methods to access and set
 * the selector working state.
 *
 * @param <S> The type of state specified by the implementing selector, not to be confused with the
 *     world state.
 */
public abstract class AbstractStatefulTransactionSelector<S> extends AbstractTransactionSelector {
  private final SelectorsStateManager selectorsStateManager;

  public AbstractStatefulTransactionSelector(
      final BlockSelectionContext context,
      final SelectorsStateManager selectorsStateManager,
      final S initialState,
      final SelectorsStateManager.StateDuplicator<S> stateDuplicator) {
    super(context);
    this.selectorsStateManager = selectorsStateManager;
    selectorsStateManager.createSelectorState(this, initialState, stateDuplicator);
  }

  /**
   * Get the working state specific to this selector.
   *
   * @return the working state of the selector
   */
  protected S getWorkingState() {
    return selectorsStateManager.getSelectorWorkingState(this);
  }

  /**
   * Set the working state specific to this selector.
   *
   * @param newState the new state of the selector
   */
  protected void setWorkingState(final S newState) {
    selectorsStateManager.setSelectorWorkingState(this, newState);
  }
}
