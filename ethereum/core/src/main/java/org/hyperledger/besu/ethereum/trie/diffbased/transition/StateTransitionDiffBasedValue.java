/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.trie.diffbased.transition;

import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedValue;

import java.util.Objects;

public class StateTransitionDiffBasedValue<T> extends DiffBasedValue<T> {

  public StateTransitionDiffBasedValue(final T prior, final T updated) {
    super(prior, updated);
  }

  public StateTransitionDiffBasedValue(
      final T prior, final T updated, final boolean lastStepCleared) {
    super(prior, updated, lastStepCleared);
  }

  public StateTransitionDiffBasedValue(
      final T prior,
      final T updated,
      final boolean lastStepCleared,
      final boolean clearedAtLeastOnce) {
    super(prior, updated, lastStepCleared, clearedAtLeastOnce);
  }

  @Override
  public boolean isUnchanged() {
    return Objects.equals(updated, prior);
  }

  @Override
  public T getPrior() {
    return null; // force null for prior because this element is not coming from the verkle state
    // but from the PMT
  }
}
