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

import org.apache.tuweni.units.bigints.UInt256;

public class BonsaiValue<T> {
  private T original;
  private T updated;

  BonsaiValue(final T original, final T updated) {
    if (original instanceof UInt256 &&  ((UInt256)original).toShortHexString().equals("0xbaa70")) {
      new Exception("Setting original to baa70").printStackTrace(System.out);
    }
    this.original = original;
    if (updated == null) {
      new Exception("Setting updated to null").printStackTrace(System.out);
    }
    this.updated = updated;
  }

  public T getOriginal() {
    return original;
  }

  public T getUpdated() {
    return updated;
  }

  public T getEffective() {
    return updated != null ? updated : original;
  }

  public void setOriginal(final T original) {
    this.original = original;
  }

  public void setUpdated(final T updated) {
    if (updated == null) {
      new Exception("Setting updated to null").printStackTrace(System.out);
    }
    this.updated = updated;
  }

  void writeRlp(final RLPOutput output, final BiConsumer<RLPOutput, T> writer) {
    output.startList();
    writeInnerRlp(output, writer);
    output.endList();
  }

  void writeInnerRlp(final RLPOutput output, final BiConsumer<RLPOutput, T> writer) {
    if (original == null) {
      output.writeNull();
    } else {
      writer.accept(output, original);
    }
    if (updated == null) {
      output.writeNull();
    } else {
      writer.accept(output, updated);
    }
  }

  boolean isUnchanged() {
    return Objects.equals(updated, original);
  }

  @Override
  public String toString() {
    return "BonsaiValue{" + "original=" + original + ", updated=" + updated + '}';
  }

  @Override
  public String toString() {
    return "BonsaiValue{" +
        "original=" + original +
        ", updated=" + updated +
        '}';
  }
}
