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
package org.hyperledger.besu.metrics.opentelemetry;

import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableSet;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.Labels;
import io.opentelemetry.api.metrics.DoubleValueRecorder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceAttributes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Metrics system relying on the native OpenTelemetry format. */
public class OpenTelemetrySystem implements ObservableMetricsSystem {

  private static final Logger LOG = LogManager.getLogger();

  private static final String TYPE_LABEL_KEY = "type";
  private static final String AREA_LABEL_KEY = "area";
  private static final String POOL_LABEL_KEY = "pool";
  private static final String USED = "used";
  private static final String COMMITTED = "committed";
  private static final String MAX = "max";
  private static final String HEAP = "heap";
  private static final String NON_HEAP = "non_heap";

  private final Set<MetricCategory> enabledCategories;
  private final boolean timersEnabled;
  private final Map<String, LabelledMetric<Counter>> cachedCounters = new ConcurrentHashMap<>();
  private final Map<String, LabelledMetric<OperationTimer>> cachedTimers =
      new ConcurrentHashMap<>();
  private final SdkMeterProvider meterSdkProvider;

  public OpenTelemetrySystem(
      final Set<MetricCategory> enabledCategories,
      final boolean timersEnabled,
      final String jobName) {
    LOG.info("Starting OpenTelemetry metrics system");
    this.enabledCategories = ImmutableSet.copyOf(enabledCategories);
    this.timersEnabled = timersEnabled;
    Resource resource =
        Resource.getDefault()
            .merge(
                Resource.create(
                    Attributes.builder().put(ResourceAttributes.SERVICE_NAME, jobName).build()));
    this.meterSdkProvider = SdkMeterProvider.builder().setResource(resource).build();
  }

  SdkMeterProvider getMeterSdkProvider() {
    return meterSdkProvider;
  }

  @Override
  public Stream<Observation> streamObservations(final MetricCategory category) {
    return streamObservations().filter(metricData -> metricData.getCategory().equals(category));
  }

  @Override
  public Stream<Observation> streamObservations() {
    Collection<MetricData> metricsList = meterSdkProvider.getMetricProducer().collectAllMetrics();
    return metricsList.stream().map(this::convertToObservations).flatMap(stream -> stream);
  }

  private Stream<Observation> convertToObservations(final MetricData metricData) {
    List<Observation> observations = new ArrayList<>();
    MetricCategory category =
        categoryNameToMetricCategory(metricData.getInstrumentationLibraryInfo().getName());
    for (MetricData.Point point : metricData.getPoints()) {
      List<String> labels = new ArrayList<>();
      point.getLabels().forEach((k, v) -> labels.add(v));
      observations.add(
          new Observation(
              category, metricData.getName(), extractValue(metricData.getType(), point), labels));
    }
    return observations.stream();
  }

  private MetricCategory categoryNameToMetricCategory(final String name) {
    Set<MetricCategory> categories =
        ImmutableSet.<MetricCategory>builder()
            .addAll(EnumSet.allOf(BesuMetricCategory.class))
            .addAll(EnumSet.allOf(StandardMetricCategory.class))
            .build();
    for (MetricCategory category : categories) {
      if (category.getName().equals(name)) {
        return category;
      }
    }
    throw new IllegalArgumentException("Invalid metric category: " + name);
  }

  private Object extractValue(final MetricData.Type type, final MetricData.Point point) {
    switch (type) {
      case LONG_GAUGE:
      case LONG_SUM:
        return ((MetricData.LongPoint) point).getValue();
      case DOUBLE_GAUGE:
        return ((MetricData.DoublePoint) point).getValue();
      case SUMMARY:
        return ((MetricData.DoubleSummaryPoint) point).getPercentileValues();
      default:
        throw new UnsupportedOperationException("Unsupported type " + type);
    }
  }

  @Override
  public LabelledMetric<Counter> createLabelledCounter(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    LOG.trace("Creating a counter {}", name);
    return cachedCounters.computeIfAbsent(
        name,
        (k) -> {
          if (isCategoryEnabled(category)) {
            final Meter meter = meterSdkProvider.get(category.getName());

            final LongCounter counter = meter.longCounterBuilder(name).setDescription(help).build();
            return new OpenTelemetryCounter(counter, labelNames);
          } else {
            return NoOpMetricsSystem.getCounterLabelledMetric(labelNames.length);
          }
        });
  }

  @Override
  public LabelledMetric<OperationTimer> createLabelledTimer(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    LOG.trace("Creating a timer {}", name);
    return cachedTimers.computeIfAbsent(
        name,
        (k) -> {
          if (timersEnabled && isCategoryEnabled(category)) {
            final Meter meter = meterSdkProvider.get(category.getName());

            final DoubleValueRecorder recorder =
                meter.doubleValueRecorderBuilder(name).setDescription(help).build();
            return new OpenTelemetryTimer(recorder, labelNames);
          } else {
            return NoOpMetricsSystem.getOperationTimerLabelledMetric(labelNames.length);
          }
        });
  }

  @Override
  public void createGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final DoubleSupplier valueSupplier) {
    LOG.trace("Creating a gauge {}", name);
    if (isCategoryEnabled(category)) {
      final Meter meter = meterSdkProvider.get(category.getName());
      meter
          .doubleValueObserverBuilder(name)
          .setDescription(help)
          .setUpdater(
              res -> {
                res.observe(valueSupplier.getAsDouble(), Labels.empty());
              })
          .build();
    }
  }

  @Override
  public Set<MetricCategory> getEnabledCategories() {
    return enabledCategories;
  }

  public void initDefaults() {
    if (isCategoryEnabled(StandardMetricCategory.JVM)) {
      collectGC();
    }
  }

  private void collectGC() {
    final List<GarbageCollectorMXBean> garbageCollectors =
        ManagementFactory.getGarbageCollectorMXBeans();
    final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    final List<MemoryPoolMXBean> poolBeans = ManagementFactory.getMemoryPoolMXBeans();
    final Meter meter = meterSdkProvider.get(StandardMetricCategory.JVM.getName());
    final List<Labels> labelSets = new ArrayList<>(garbageCollectors.size());
    for (final GarbageCollectorMXBean gc : garbageCollectors) {
      labelSets.add(Labels.of("gc", gc.getName()));
    }
    meter
        .longSumObserverBuilder("jvm.gc.collection")
        .setDescription("Time spent in a given JVM garbage collector in milliseconds.")
        .setUnit("ms")
        .setUpdater(
            resultLongObserver -> {
              for (int i = 0; i < garbageCollectors.size(); i++) {
                resultLongObserver.observe(
                    garbageCollectors.get(i).getCollectionTime(), labelSets.get(i));
              }
            })
        .build();
    final Labels usedHeap = Labels.of(TYPE_LABEL_KEY, USED, AREA_LABEL_KEY, HEAP);
    final Labels usedNonHeap = Labels.of(TYPE_LABEL_KEY, USED, AREA_LABEL_KEY, NON_HEAP);
    final Labels committedHeap = Labels.of(TYPE_LABEL_KEY, COMMITTED, AREA_LABEL_KEY, HEAP);
    final Labels committedNonHeap = Labels.of(TYPE_LABEL_KEY, COMMITTED, AREA_LABEL_KEY, NON_HEAP);
    // TODO: Decide if max is needed or not. May be derived with some approximation from max(used).
    final Labels maxHeap = Labels.of(TYPE_LABEL_KEY, MAX, AREA_LABEL_KEY, HEAP);
    final Labels maxNonHeap = Labels.of(TYPE_LABEL_KEY, MAX, AREA_LABEL_KEY, NON_HEAP);
    meter
        .longUpDownSumObserverBuilder("jvm.memory.area")
        .setDescription("Bytes of a given JVM memory area.")
        .setUnit("By")
        .setUpdater(
            resultLongObserver -> {
              MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
              MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
              resultLongObserver.observe(heapUsage.getUsed(), usedHeap);
              resultLongObserver.observe(nonHeapUsage.getUsed(), usedNonHeap);
              resultLongObserver.observe(heapUsage.getUsed(), committedHeap);
              resultLongObserver.observe(nonHeapUsage.getUsed(), committedNonHeap);
              resultLongObserver.observe(heapUsage.getUsed(), maxHeap);
              resultLongObserver.observe(nonHeapUsage.getUsed(), maxNonHeap);
            })
        .build();
    final List<Labels> usedLabelSets = new ArrayList<>(poolBeans.size());
    final List<Labels> committedLabelSets = new ArrayList<>(poolBeans.size());
    final List<Labels> maxLabelSets = new ArrayList<>(poolBeans.size());
    for (final MemoryPoolMXBean pool : poolBeans) {
      usedLabelSets.add(Labels.of(TYPE_LABEL_KEY, USED, POOL_LABEL_KEY, pool.getName()));
      committedLabelSets.add(Labels.of(TYPE_LABEL_KEY, COMMITTED, POOL_LABEL_KEY, pool.getName()));
      maxLabelSets.add(Labels.of(TYPE_LABEL_KEY, MAX, POOL_LABEL_KEY, pool.getName()));
    }

    meter
        .longUpDownSumObserverBuilder("jvm.memory.pool")
        .setDescription("Bytes of a given JVM memory pool.")
        .setUnit("By")
        .setUpdater(
            resultLongObserver -> {
              for (int i = 0; i < poolBeans.size(); i++) {
                MemoryUsage poolUsage = poolBeans.get(i).getUsage();
                resultLongObserver.observe(poolUsage.getUsed(), usedLabelSets.get(i));
                resultLongObserver.observe(poolUsage.getCommitted(), committedLabelSets.get(i));
                // TODO: Decide if max is needed or not. May be derived with some approximation from
                //  max(used).
                resultLongObserver.observe(poolUsage.getMax(), maxLabelSets.get(i));
              }
            })
        .build();
  }
}
