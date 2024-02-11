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
 *
 */

package org.hyperledger.besu.ethereum.trie.bonsai;

import org.hyperledger.besu.plugin.services.trielogs.TrieLog;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class BonsaiValue<T> implements TrieLog.LogTuple<T> {
  private T prior;
  private T updated;
  private boolean lastStepCleared;

  private boolean clearedAtLeastOnce;

  public BonsaiValue(final T prior, final T updated) {
    this.prior = prior;
    this.updated = updated;
    this.lastStepCleared = false;
    this.clearedAtLeastOnce = false;
  }

  public BonsaiValue(final T prior, final T updated, final boolean lastStepCleared) {
    this.prior = prior;
    this.updated = updated;
    this.lastStepCleared = lastStepCleared;
    this.clearedAtLeastOnce = lastStepCleared;
  }

  public BonsaiValue(
      final T prior,
      final T updated,
      final boolean lastStepCleared,
      final boolean clearedAtLeastOnce) {
    this.prior = prior;
    this.updated = updated;
    this.lastStepCleared = lastStepCleared;
    this.clearedAtLeastOnce = clearedAtLeastOnce;
  }

  @Override
  public T getPrior() {
    return prior;
  }

  @Override
  public T getUpdated() {
    return updated;
  }

  public BonsaiValue<T> setPrior(final T prior) {
    this.prior = prior;
    return this;
  }

  public BonsaiValue<T> setUpdated(final T updated) {
    this.lastStepCleared = updated == null;
    if (lastStepCleared) {
      this.clearedAtLeastOnce = true;
    }
    this.updated = updated;
    return this;
  }

  public void setCleared() {
    this.lastStepCleared = true;
    this.clearedAtLeastOnce = true;
  }

  @Override
  public boolean isLastStepCleared() {
    return lastStepCleared;
  }

  @Override
  public boolean isClearedAtLeastOnce() {
    return clearedAtLeastOnce;
  }

  @Override
  public String toString() {
    return "BonsaiValue{"
        + "prior="
        + prior
        + ", updated="
        + updated
        + ", cleared="
        + lastStepCleared
        + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BonsaiValue<?> that = (BonsaiValue<?>) o;
    return new EqualsBuilder()
        .append(lastStepCleared, that.lastStepCleared)
        .append(prior, that.prior)
        .append(updated, that.updated)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(prior)
        .append(updated)
        .append(lastStepCleared)
        .toHashCode();
  }

  public BonsaiValue<T> copy() {
    return new BonsaiValue<T>(prior, updated, lastStepCleared, clearedAtLeastOnce);
  }
}
