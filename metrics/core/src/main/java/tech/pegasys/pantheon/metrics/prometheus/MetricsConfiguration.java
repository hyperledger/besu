/*
 * Copyright 2019 ConsenSys AG.
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

import static tech.pegasys.pantheon.metrics.PantheonMetricCategory.DEFAULT_METRIC_CATEGORIES;

import tech.pegasys.pantheon.plugin.services.metrics.MetricCategory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.common.base.MoreObjects;

public class MetricsConfiguration {
  private static final String DEFAULT_METRICS_HOST = "127.0.0.1";
  public static final int DEFAULT_METRICS_PORT = 9545;

  private static final String DEFAULT_METRICS_PUSH_HOST = "127.0.0.1";
  public static final int DEFAULT_METRICS_PUSH_PORT = 9001;
  public static final Boolean DEFAULT_TIMERS_ENABLED = true;

  private final boolean enabled;
  private final int port;
  private int actualPort;
  private final String host;
  private final Set<MetricCategory> metricCategories;
  private final boolean pushEnabled;
  private final int pushPort;
  private final String pushHost;
  private final int pushInterval;
  private final String prometheusJob;
  private final List<String> hostsWhitelist;
  private final boolean timersEnabled;

  public static Builder builder() {
    return new Builder();
  }

  private MetricsConfiguration(
      final boolean enabled,
      final int port,
      final String host,
      final Set<MetricCategory> metricCategories,
      final boolean pushEnabled,
      final int pushPort,
      final String pushHost,
      final int pushInterval,
      final String prometheusJob,
      final List<String> hostsWhitelist,
      final boolean timersEnabled) {
    this.enabled = enabled;
    this.port = port;
    this.host = host;
    this.metricCategories = metricCategories;
    this.pushEnabled = pushEnabled;
    this.pushPort = pushPort;
    this.pushHost = pushHost;
    this.pushInterval = pushInterval;
    this.prometheusJob = prometheusJob;
    this.hostsWhitelist = hostsWhitelist;
    this.timersEnabled = timersEnabled;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public int getActualPort() {
    return actualPort;
  }

  void setActualPort(final int actualPort) {
    this.actualPort = actualPort;
  }

  public Set<MetricCategory> getMetricCategories() {
    return metricCategories;
  }

  public int getPushPort() {
    return pushPort;
  }

  public String getPushHost() {
    return pushHost;
  }

  public boolean isPushEnabled() {
    return pushEnabled;
  }

  public int getPushInterval() {
    return pushInterval;
  }

  public String getPrometheusJob() {
    return prometheusJob;
  }

  Collection<String> getHostsWhitelist() {
    return Collections.unmodifiableCollection(this.hostsWhitelist);
  }

  public boolean isTimersEnabled() {
    return timersEnabled;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("enabled", enabled)
        .add("port", port)
        .add("host", host)
        .add("metricCategories", metricCategories)
        .add("pushEnabled", pushEnabled)
        .add("pushPort", pushPort)
        .add("pushHost", pushHost)
        .add("pushInterval", pushInterval)
        .add("prometheusJob", prometheusJob)
        .add("hostsWhitelist", hostsWhitelist)
        .toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MetricsConfiguration that = (MetricsConfiguration) o;
    return enabled == that.enabled
        && port == that.port
        && pushEnabled == that.pushEnabled
        && pushPort == that.pushPort
        && pushInterval == that.pushInterval
        && Objects.equals(host, that.host)
        && Objects.equals(metricCategories, that.metricCategories)
        && Objects.equals(pushHost, that.pushHost)
        && Objects.equals(prometheusJob, that.prometheusJob)
        && Objects.equals(hostsWhitelist, that.hostsWhitelist);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        enabled,
        port,
        host,
        metricCategories,
        pushEnabled,
        pushPort,
        pushHost,
        pushInterval,
        prometheusJob,
        hostsWhitelist);
  }

  public static class Builder {
    private boolean enabled = false;
    private int port = DEFAULT_METRICS_PORT;
    private String host = DEFAULT_METRICS_HOST;
    private Set<MetricCategory> metricCategories = DEFAULT_METRIC_CATEGORIES;
    private boolean pushEnabled = false;
    private int pushPort = DEFAULT_METRICS_PUSH_PORT;
    private String pushHost = DEFAULT_METRICS_PUSH_HOST;
    private int pushInterval = 15;
    private String prometheusJob = "pantheon-client";
    private List<String> hostsWhitelist = Arrays.asList("localhost", "127.0.0.1");
    private boolean timersEnabled = DEFAULT_TIMERS_ENABLED;

    private Builder() {}

    public Builder enabled(final boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public Builder port(final int port) {
      this.port = port;
      return this;
    }

    public Builder host(final String host) {
      this.host = host;
      return this;
    }

    public Builder metricCategories(final Set<MetricCategory> metricCategories) {
      this.metricCategories = metricCategories;
      return this;
    }

    public Builder pushEnabled(final boolean pushEnabled) {
      this.pushEnabled = pushEnabled;
      return this;
    }

    public Builder pushPort(final int pushPort) {
      this.pushPort = pushPort;
      return this;
    }

    public Builder pushHost(final String pushHost) {
      this.pushHost = pushHost;
      return this;
    }

    public Builder pushInterval(final int pushInterval) {
      this.pushInterval = pushInterval;
      return this;
    }

    public Builder prometheusJob(final String prometheusJob) {
      this.prometheusJob = prometheusJob;
      return this;
    }

    public Builder hostsWhitelist(final List<String> hostsWhitelist) {
      this.hostsWhitelist = hostsWhitelist;
      return this;
    }

    public Builder timersEnabled(final boolean timersEnabled) {
      this.timersEnabled = timersEnabled;
      return this;
    }

    public MetricsConfiguration build() {
      return new MetricsConfiguration(
          enabled,
          port,
          host,
          metricCategories,
          pushEnabled,
          pushPort,
          pushHost,
          pushInterval,
          prometheusJob,
          hostsWhitelist,
          timersEnabled);
    }
  }
}
