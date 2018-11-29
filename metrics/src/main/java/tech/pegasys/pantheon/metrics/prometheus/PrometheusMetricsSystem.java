/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.metrics.prometheus;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;

import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.Observation;
import tech.pegasys.pantheon.metrics.OperationTimer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.Collector.Type;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import io.prometheus.client.hotspot.BufferPoolsExports;
import io.prometheus.client.hotspot.ClassLoadingExports;
import io.prometheus.client.hotspot.GarbageCollectorExports;
import io.prometheus.client.hotspot.MemoryPoolsExports;
import io.prometheus.client.hotspot.StandardExports;
import io.prometheus.client.hotspot.ThreadExports;

public class PrometheusMetricsSystem implements MetricsSystem {

  private static final String PANTHEON_PREFIX = "pantheon_";
  private final Map<MetricCategory, Collection<Collector>> collectors = new ConcurrentHashMap<>();

  PrometheusMetricsSystem() {}

  public static MetricsSystem init() {
    final PrometheusMetricsSystem metricsSystem = new PrometheusMetricsSystem();
    metricsSystem.collectors.put(MetricCategory.PROCESS, singleton(new StandardExports()));
    metricsSystem.collectors.put(
        MetricCategory.JVM,
        asList(
            new MemoryPoolsExports(),
            new BufferPoolsExports(),
            new GarbageCollectorExports(),
            new ThreadExports(),
            new ClassLoadingExports()));
    return metricsSystem;
  }

  @Override
  public LabelledMetric<tech.pegasys.pantheon.metrics.Counter> createLabelledCounter(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    final Counter counter =
        Counter.build(convertToPrometheusName(category, name), help)
            .labelNames(labelNames)
            .create();
    addCollector(category, counter);
    return new PrometheusCounter(counter);
  }

  @Override
  public LabelledMetric<OperationTimer> createLabelledTimer(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    final Histogram histogram = Histogram.build(name, help).labelNames(labelNames).create();
    addCollector(category, histogram);
    return new PrometheusTimer(histogram);
  }

  @Override
  public void createGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final Supplier<Double> valueSupplier) {
    final String metricName = convertToPrometheusName(category, name);
    addCollector(category, new CurrentValueCollector(metricName, help, valueSupplier));
  }

  private void addCollector(final MetricCategory category, final Collector counter) {
    collectors
        .computeIfAbsent(category, key -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
        .add(counter);
  }

  @Override
  public Stream<Observation> getMetrics(final MetricCategory category) {
    return collectors
        .getOrDefault(category, Collections.emptySet())
        .stream()
        .flatMap(collector -> collector.collect().stream())
        .flatMap(familySamples -> convertSamplesToObservations(category, familySamples));
  }

  private Stream<Observation> convertSamplesToObservations(
      final MetricCategory category, final MetricFamilySamples familySamples) {
    return familySamples
        .samples
        .stream()
        .map(sample -> createObservationFromSample(category, sample, familySamples));
  }

  private Observation createObservationFromSample(
      final MetricCategory category, final Sample sample, final MetricFamilySamples familySamples) {
    if (familySamples.type == Type.HISTOGRAM) {
      return convertHistogramSampleNamesToLabels(category, sample, familySamples);
    }
    return new Observation(
        category,
        convertFromPrometheusName(category, sample.name),
        sample.value,
        sample.labelValues);
  }

  private Observation convertHistogramSampleNamesToLabels(
      final MetricCategory category, final Sample sample, final MetricFamilySamples familySamples) {
    final List<String> labelValues = new ArrayList<>(sample.labelValues);
    if (sample.name.endsWith("_bucket")) {
      labelValues.add(labelValues.size() - 1, "bucket");
    } else {
      labelValues.add(sample.name.substring(sample.name.lastIndexOf("_") + 1));
    }
    return new Observation(
        category,
        convertFromPrometheusName(category, familySamples.name),
        sample.value,
        labelValues);
  }

  private String convertToPrometheusName(final MetricCategory category, final String name) {
    return prometheusPrefix(category) + name;
  }

  private String convertFromPrometheusName(final MetricCategory category, final String metricName) {
    final String prefix = prometheusPrefix(category);
    return metricName.startsWith(prefix) ? metricName.substring(prefix.length()) : metricName;
  }

  private String prometheusPrefix(final MetricCategory category) {
    return category.isPantheonSpecific()
        ? PANTHEON_PREFIX + category.getName() + "_"
        : category.getName() + "_";
  }
}
