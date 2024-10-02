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
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedValue;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nonnull;

import com.google.common.collect.ForwardingMap;
import org.apache.tuweni.bytes.Bytes;

public class CodeConsumingMap extends ForwardingMap<Address, DiffBasedValue<Bytes>> {

  private final ConcurrentMap<Address, DiffBasedValue<Bytes>> codes;
  private final Consumer<Bytes> consumer;

  public CodeConsumingMap(
      final ConcurrentMap<Address, DiffBasedValue<Bytes>> codes, final Consumer<Bytes> consumer) {
    this.codes = codes;
    this.consumer = consumer;
  }

  @Override
  public DiffBasedValue<Bytes> put(
      @Nonnull final Address address, @Nonnull final DiffBasedValue<Bytes> value) {
    consumer.process(address, value.getUpdated() != null ? value.getUpdated() : value.getPrior());
    return codes.put(address, value);
  }

  public Consumer<Bytes> getConsumer() {
    return consumer;
  }

  @Override
  protected Map<Address, DiffBasedValue<Bytes>> delegate() {
    return codes;
  }
}
