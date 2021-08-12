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

package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.ethereum.vm.Code;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;

public class CodeCache implements LoadingCache<Account, Code> {

  private final LoadingCache<Account, Code> cache;

  public CodeCache(final long maxWeight) {
    this.cache =
        CacheBuilder.newBuilder()
            .maximumWeight(maxWeight)
            .weigher(new CodeScale())
            .build(new CodeLoader());
  }

  public CodeCache() {
    this(1024L * 1024L);
  }

  public Optional<Code> getContract(final Account account) {
    if (account != null && account.hasCode()) {
      return Optional.of(this.getUnchecked(account));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Code get(final Account key) throws ExecutionException {
    return this.cache.get(key);
  }

  @Override
  public Code getUnchecked(final Account key) {
    return this.cache.getUnchecked(key);
  }

  @Override
  public ImmutableMap<Account, Code> getAll(final Iterable<? extends Account> keys)
      throws ExecutionException {
    return this.cache.getAll(keys);
  }

  @Override
  public Code apply(final Account key) {
    return this.cache.apply(key);
  }

  @Override
  public void refresh(final Account key) {
    this.cache.refresh(key);
  }

  @Override
  public @Nullable Code getIfPresent(final Object key) {
    return this.cache.getIfPresent(key);
  }

  @Override
  public Code get(final Account key, final Callable<? extends Code> loader)
      throws ExecutionException {
    return this.cache.get(key, loader);
  }

  @Override
  public ImmutableMap<Account, Code> getAllPresent(final Iterable<?> keys) {
    return this.cache.getAllPresent(keys);
  }

  @Override
  public void put(final Account key, final Code value) {
    this.cache.put(key, value);
  }

  @Override
  public void putAll(final Map<? extends Account, ? extends Code> m) {
    this.cache.putAll(m);
  }

  @Override
  public void invalidate(final Object key) {
    if (key != null) {
      this.cache.invalidate(key);
    }
  }

  public void invalidate(final Account key) {
    if (key != null) {
      this.cache.invalidate(key);
    }
  }

  @Override
  public void invalidateAll(final Iterable<?> keys) {
    this.cache.invalidateAll(keys);
  }

  @Override
  public void invalidateAll() {
    this.cache.invalidateAll();
  }

  @Override
  public long size() {
    return this.cache.size();
  }

  @Override
  public CacheStats stats() {
    return this.cache.stats();
  }

  @Override
  public ConcurrentMap<Account, Code> asMap() {
    return this.cache.asMap();
  }

  @Override
  public void cleanUp() {
    this.cache.cleanUp();
  }
}
