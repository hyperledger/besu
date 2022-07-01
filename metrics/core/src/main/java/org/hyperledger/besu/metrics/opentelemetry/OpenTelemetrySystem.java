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
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.DoubleHistogramPointData;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.DoubleSummaryPointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.data.PointData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Metrics system relying on the native OpenTelemetry format. */
public class OpenTelemetrySystem implements ObservableMetricsSystem {

  private static final Logger LOG = LoggerFactory.getLogger(OpenTelemetrySystem.class);

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
    Collection<MetricData> metricsList = meterSdkProvider.collectAllMetrics();
    return metricsList.stream().map(this::convertToObservations).flatMap(stream -> stream);
  }

  private Stream<Observation> convertToObservations(final MetricData metricData) {
    List<Observation> observations = new ArrayList<>();
    MetricCategory category =
        categoryNameToMetricCategory(metricData.getInstrumentationLibraryInfo().getName());
    Collection<?> points;
    switch (metricData.getType()) {
      case DOUBLE_GAUGE:
        points = metricData.getDoubleGaugeData().getPoints();
        break;
      case DOUBLE_SUM:
        points = metricData.getDoubleSumData().getPoints();
        break;
      case SUMMARY:
        points = metricData.getDoubleSummaryData().getPoints();
        break;
      case LONG_SUM:
        points = metricData.getLongSumData().getPoints();
        break;
      case HISTOGRAM:
        points = metricData.getDoubleHistogramData().getPoints();
        break;
      case LONG_GAUGE:
        points = metricData.getLongGaugeData().getPoints();
        break;
      default:
        throw new UnsupportedOperationException("Unsupported type " + metricData.getType().name());
    }

    for (Object ptObj : points) {
      PointData point = (PointData) ptObj;
      List<String> labels = new ArrayList<>();
      point.getAttributes().forEach((k, v) -> labels.add(v.toString()));
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

  private Object extractValue(final MetricDataType type, final PointData point) {
    switch (type) {
      case LONG_GAUGE:
      case LONG_SUM:
        return ((LongPointData) point).getValue();
      case DOUBLE_GAUGE:
        return ((DoublePointData) point).getValue();
      case SUMMARY:
        return ((DoubleSummaryPointData) point).getPercentileValues();
      case HISTOGRAM:
        return ((DoubleHistogramPointData) point).getCounts();
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

            final LongCounter counter = meter.counterBuilder(name).setDescription(help).build();
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
            return new OpenTelemetryTimer(name, help, meter, labelNames);
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
          .gaugeBuilder(name)
          .setDescription(help)
          .buildWithCallback(res -> res.observe(valueSupplier.getAsDouble(), Attributes.empty()));
    }
  }

  @Override
  public LabelledGauge createLabelledGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    LOG.trace("Creating a labelled gauge {}", name);
    if (isCategoryEnabled(category)) {
      return new OpenTelemetryGauge(
          name, help, meterSdkProvider.get(category.getName()), List.of(labelNames));
    }
    return NoOpMetricsSystem.getLabelledGauge(labelNames.length);
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
    final List<Attributes> labelSets = new ArrayList<>(garbageCollectors.size());
    for (final GarbageCollectorMXBean gc : garbageCollectors) {
      labelSets.add(Attributes.of(AttributeKey.stringKey("gc"), gc.getName()));
    }
    meter
        .gaugeBuilder("jvm.gc.collection")
        .setDescription("Time spent in a given JVM garbage collector in milliseconds.")
        .setUnit("ms")
        .buildWithCallback(
            resultLongObserver -> {
              for (int i = 0; i < garbageCollectors.size(); i++) {
                resultLongObserver.observe(
                    (double) garbageCollectors.get(i).getCollectionTime(), labelSets.get(i));
              }
            });
    final AttributeKey<String> typeKey = AttributeKey.stringKey(TYPE_LABEL_KEY);
    final AttributeKey<String> areaKey = AttributeKey.stringKey(AREA_LABEL_KEY);
    final Attributes usedHeap = Attributes.of(typeKey, USED, areaKey, HEAP);
    final Attributes usedNonHeap = Attributes.of(typeKey, USED, areaKey, NON_HEAP);
    final Attributes committedHeap = Attributes.of(typeKey, COMMITTED, areaKey, HEAP);
    final Attributes committedNonHeap = Attributes.of(typeKey, COMMITTED, areaKey, NON_HEAP);
    // TODO: Decide if max is needed or not. May be derived with some approximation from max(used).
    final Attributes maxHeap = Attributes.of(typeKey, MAX, areaKey, HEAP);
    final Attributes maxNonHeap = Attributes.of(typeKey, MAX, areaKey, NON_HEAP);
    meter
        .upDownCounterBuilder("jvm.memory.area")
        .setDescription("Bytes of a given JVM memory area.")
        .setUnit("By")
        .buildWithCallback(
            resultLongObserver -> {
              MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
              MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
              resultLongObserver.observe(heapUsage.getUsed(), usedHeap);
              resultLongObserver.observe(nonHeapUsage.getUsed(), usedNonHeap);
              resultLongObserver.observe(heapUsage.getUsed(), committedHeap);
              resultLongObserver.observe(nonHeapUsage.getUsed(), committedNonHeap);
              resultLongObserver.observe(heapUsage.getUsed(), maxHeap);
              resultLongObserver.observe(nonHeapUsage.getUsed(), maxNonHeap);
            });
    final List<Attributes> usedLabelSets = new ArrayList<>(poolBeans.size());
    final List<Attributes> committedLabelSets = new ArrayList<>(poolBeans.size());
    final List<Attributes> maxLabelSets = new ArrayList<>(poolBeans.size());
    final AttributeKey<String> poolKey = AttributeKey.stringKey(POOL_LABEL_KEY);
    for (final MemoryPoolMXBean pool : poolBeans) {
      usedLabelSets.add(Attributes.of(typeKey, USED, poolKey, pool.getName()));
      committedLabelSets.add(Attributes.of(typeKey, COMMITTED, poolKey, pool.getName()));
      maxLabelSets.add(Attributes.of(typeKey, MAX, poolKey, pool.getName()));
    }

    meter
        .upDownCounterBuilder("jvm.memory.pool")
        .setDescription("Bytes of a given JVM memory pool.")
        .setUnit("By")
        .buildWithCallback(
            resultLongObserver -> {
              for (int i = 0; i < poolBeans.size(); i++) {
                MemoryUsage poolUsage = poolBeans.get(i).getUsage();
                resultLongObserver.observe(poolUsage.getUsed(), usedLabelSets.get(i));
                resultLongObserver.observe(poolUsage.getCommitted(), committedLabelSets.get(i));
                // TODO: Decide if max is needed or not. May be derived with some approximation from
                //  max(used).
                resultLongObserver.observe(poolUsage.getMax(), maxLabelSets.get(i));
              }
            });
  }
}
