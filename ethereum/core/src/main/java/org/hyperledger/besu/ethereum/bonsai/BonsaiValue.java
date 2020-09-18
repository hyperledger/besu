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

public class BonsaiValue<T> {
  private T original;
  private T updated;

  public BonsaiValue(final T original, final T updated) {
    this.original = original;
    this.updated = updated;
  }

  public T getOriginal() {
    return original;
  }

  public T getUpdated() {
    return updated;
  }

  public void setOriginal(final T original) {
    this.original = original;
  }

  public void setUpdated(final T updated) {
    this.updated = updated;
  }

  T effective() {
    return updated == null ? original : updated;
  }
}
