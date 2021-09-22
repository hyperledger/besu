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
 */

package org.hyperledger.besu.ethereum.core.contract;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.vm.Code;

import java.util.Optional;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

public class CodeCache {

  private final LoadingCache<ImmutableCodeHash, Code> cache;
  private final long weight;

  public CodeCache(final long maxWeightBytes, final CodeLoader loader) {
    this.weight = maxWeightBytes;
    this.cache =
        Caffeine.newBuilder().maximumWeight(maxWeightBytes).weigher(new CodeScale()).build(loader);
  }

  public CodeCache(final long maxWeightBytes) {
    this(maxWeightBytes, new CodeLoader());
  }

  public Optional<Code> getContract(final Account account) {
    if (account != null && account.hasCode()) {
      return Optional.of(cache.get(ImmutableCodeHash.of(account.getCodeHash(), account.getCode())));
    } else {
      return Optional.empty();
    }
  }

  public void invalidate(final Account key) {
    if (key != null && key.hasCode()) {
      this.cache.invalidate(ImmutableCodeHash.of(key.getCodeHash(), key.getCode()));
    }
  }

  public void cleanUp() {
    this.cache.cleanUp();
  }

  public Code getIfPresent(final Account contract) {
    if (contract != null && contract.hasCode()) {
      return cache.getIfPresent(
          ImmutableCodeHash.of(contract.getAddressHash(), contract.getCode()));
    } else {
      return null;
    }
  }

  public long size() {
    cache.cleanUp();
    return cache.estimatedSize();
  }

  public long getWeight() {
    return weight;
  }
}
