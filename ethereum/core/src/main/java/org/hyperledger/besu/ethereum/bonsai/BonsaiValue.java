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

package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Objects;
import java.util.function.BiConsumer;

public class BonsaiValue<T> {
  private T prior;
  private T updated;
  private boolean cleared;

  BonsaiValue(final T prior, final T updated) {
    this.prior = prior;
    this.updated = updated;
    this.cleared = false;
  }

  BonsaiValue(final T prior, final T updated, final boolean cleared) {
    this.prior = prior;
    this.updated = updated;
    this.cleared = cleared;
  }

  public T getPrior() {
    return prior;
  }

  public T getUpdated() {
    return updated;
  }

  public void setPrior(final T prior) {
    this.prior = prior;
  }

  public void setUpdated(final T updated) {
    this.cleared = updated == null;
    this.updated = updated;
  }

  void writeRlp(final RLPOutput output, final BiConsumer<RLPOutput, T> writer) {
    output.startList();
    writeInnerRlp(output, writer);
    output.endList();
  }

  void writeInnerRlp(final RLPOutput output, final BiConsumer<RLPOutput, T> writer) {
    if (prior == null) {
      output.writeNull();
    } else {
      writer.accept(output, prior);
    }
    if (updated == null) {
      output.writeNull();
    } else {
      writer.accept(output, updated);
    }
  }

  boolean isUnchanged() {
    return Objects.equals(updated, prior);
  }

  public boolean isCleared() {
    return cleared;
  }

  @Override
  public String toString() {
    return "BonsaiValue{"
        + "prior="
        + prior
        + ", updated="
        + updated
        + ", cleared="
        + cleared
        + '}';
  }
}
