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
package org.hyperledger.besu.metrics.prometheus;

import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import com.google.common.cache.Cache;
import io.prometheus.metrics.instrumentation.guava.CacheMetricsCollector;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.vertx.core.impl.ConcurrentHashSet;

class PrometheusGuavaCache extends CategorizedPrometheusCollector {
  private static final String NAME = "__guavaCacheMetricsCollector__";
  private static final CacheMetricsCollector cacheMetricsCollector = new CacheMetricsCollector();
  private static final Set<String> cacheNames = new ConcurrentHashSet<>();
  private static final AtomicBoolean collectorRegistered = new AtomicBoolean(false);

  private final Cache<?, ?> cache;

  public PrometheusGuavaCache(
      final MetricCategory category, final String name, final Cache<?, ?> cache) {
    super(category, NAME);
    if (cacheNames.contains(name)) {
      throw new IllegalStateException("Cache already registered: " + name);
    }
    cacheNames.add(name);
    this.cache = cache;
  }

  @Override
  public String getName() {
    return category.getName() + "." + NAME + "." + name;
  }

  @Override
  public void register(final PrometheusRegistry registry) {
    cacheMetricsCollector.addCache(name, cache);
    if (collectorRegistered.compareAndSet(false, true)) {
      registry.register(cacheMetricsCollector);
    }
  }

  @Override
  public void unregister(final PrometheusRegistry registry) {
    cacheMetricsCollector.removeCache(name);
    cacheNames.remove(name);
    if (cacheNames.isEmpty() && collectorRegistered.compareAndSet(true, false)) {
      registry.unregister(cacheMetricsCollector);
    }
  }

  @Override
  public Stream<Observation> streamObservations() {
    return Stream.empty();
  }
}
