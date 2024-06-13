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
package org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.preload;

import org.hyperledger.besu.datatypes.Address;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nonnull;

import com.google.common.collect.ForwardingMap;

public class AccountConsumingMap<T> extends ForwardingMap<Address, T> {

  private final ConcurrentMap<Address, T> accounts;
  private final Consumer<T> consumer;

  public AccountConsumingMap(final ConcurrentMap<Address, T> accounts, final Consumer<T> consumer) {
    this.accounts = accounts;
    this.consumer = consumer;
  }

  @Override
  public T put(@Nonnull final Address address, @Nonnull final T value) {
    consumer.process(address, value);
    return accounts.put(address, value);
  }

  public Consumer<T> getConsumer() {
    return consumer;
  }

  @Override
  protected Map<Address, T> delegate() {
    return accounts;
  }
}
