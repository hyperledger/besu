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
package org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.preload;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.trie.pathbased.common.PathBasedValue;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nonnull;

import com.google.common.collect.ForwardingMap;
import org.apache.tuweni.bytes.Bytes;

/**
 * A map that stores Ethereum addresses and their associated code values, with a consumer to process
 * updates.
 *
 * <p>Each time a new value is added or updated, the consumer processes the code value.
 *
 * <p>This class uses a thread-safe {@link ConcurrentMap} to store data, allowing concurrent
 * modifications.
 */
public class CodeConsumingMap extends ForwardingMap<Address, PathBasedValue<Bytes>> {

  private final ConcurrentMap<Address, PathBasedValue<Bytes>> codes;
  private final Consumer<Bytes> consumer;

  public CodeConsumingMap(
      final ConcurrentMap<Address, PathBasedValue<Bytes>> codes, final Consumer<Bytes> consumer) {
    this.codes = codes;
    this.consumer = consumer;
  }

  @Override
  public PathBasedValue<Bytes> put(
      @Nonnull final Address address, @Nonnull final PathBasedValue<Bytes> value) {
    consumer.process(address, value.getUpdated() != null ? value.getUpdated() : value.getPrior());
    return codes.put(address, value);
  }

  public Consumer<Bytes> getConsumer() {
    return consumer;
  }

  @Nonnull
  @Override
  protected Map<Address, PathBasedValue<Bytes>> delegate() {
    return codes;
  }
}
