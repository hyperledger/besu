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

import javax.annotation.Nullable;

import org.immutables.value.Value;

@Value.Immutable
@Value.Style(allParameters = true)
public interface BonsaiValue<T> {
  @Nullable
  T getOriginal();

  @Nullable
  T getUpdated();

  default T touched() {
    final T value = getUpdated();
    return value == null ? getOriginal() : value;
  }

  default BonsaiValue<T> merge(final BonsaiValue<T> original, final BonsaiValue<T> updated) {
    return ImmutableBonsaiValue.of(original.getOriginal(), updated.getUpdated());
  }

  default BonsaiValue<T> revise(final T updated) {
    return ImmutableBonsaiValue.of(getOriginal(), updated);
  }
}
