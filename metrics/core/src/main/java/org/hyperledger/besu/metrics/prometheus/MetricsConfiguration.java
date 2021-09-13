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

import static org.hyperledger.besu.metrics.BesuMetricCategory.DEFAULT_METRIC_CATEGORIES;

import org.hyperledger.besu.metrics.MetricsProtocol;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

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
  private static final MetricsProtocol DEFAULT_METRICS_PROTOCOL = MetricsProtocol.PROMETHEUS;
  private static final String DEFAULT_METRICS_PUSH_HOST = "127.0.0.1";
  public static final int DEFAULT_METRICS_PUSH_PORT = 9001;
  public static final Boolean DEFAULT_METRICS_TIMERS_ENABLED = true;
  public static final int DEFAULT_METRICS_IDLE_TIMEOUT_SECONDS = 60;

  private final boolean enabled;
  private final MetricsProtocol protocol;
  private final int port;
  private int actualPort;
  private final String host;
  private final Set<MetricCategory> metricCategories;
  private final boolean pushEnabled;
  private final int pushPort;
  private final String pushHost;
  private final int pushInterval;
  private final String prometheusJob;
  private final List<String> hostsAllowlist;
  private final boolean timersEnabled;
  private final int idleTimeout;

  public static Builder builder() {
    return new Builder();
  }

  private MetricsConfiguration(
      final boolean enabled,
      final int port,
      final MetricsProtocol protocol,
      final String host,
      final Set<MetricCategory> metricCategories,
      final boolean pushEnabled,
      final int pushPort,
      final String pushHost,
      final int pushInterval,
      final String prometheusJob,
      final List<String> hostsAllowlist,
      final boolean timersEnabled,
      final int idleTimeout) {
    this.enabled = enabled;
    this.port = port;
    this.protocol = protocol;
    this.host = host;
    this.metricCategories = metricCategories;
    this.pushEnabled = pushEnabled;
    this.pushPort = pushPort;
    this.pushHost = pushHost;
    this.pushInterval = pushInterval;
    this.prometheusJob = prometheusJob;
    this.hostsAllowlist = hostsAllowlist;
    this.timersEnabled = timersEnabled;
    this.idleTimeout = idleTimeout;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public MetricsProtocol getProtocol() {
    return protocol;
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

  // use getHostsAllowlist instead
  @Deprecated
  Collection<String> getHostsWhitelist() {
    return Collections.unmodifiableCollection(this.hostsAllowlist);
  }

  Collection<String> getHostsAllowlist() {
    return Collections.unmodifiableCollection(this.hostsAllowlist);
  }

  public boolean isTimersEnabled() {
    return timersEnabled;
  }

  public int getIdleTimeout() {
    return idleTimeout;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("enabled", enabled)
        .add("protocol", protocol)
        .add("port", port)
        .add("host", host)
        .add("metricCategories", metricCategories)
        .add("pushEnabled", pushEnabled)
        .add("pushPort", pushPort)
        .add("pushHost", pushHost)
        .add("pushInterval", pushInterval)
        .add("prometheusJob", prometheusJob)
        .add("hostsAllowlist", hostsAllowlist)
        .add("timersEnabled", timersEnabled)
        .add("idleTimeout", idleTimeout)
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
        && Objects.equals(protocol, that.protocol)
        && port == that.port
        && pushEnabled == that.pushEnabled
        && pushPort == that.pushPort
        && pushInterval == that.pushInterval
        && Objects.equals(host, that.host)
        && Objects.equals(metricCategories, that.metricCategories)
        && Objects.equals(pushHost, that.pushHost)
        && Objects.equals(prometheusJob, that.prometheusJob)
        && Objects.equals(hostsAllowlist, that.hostsAllowlist)
        && timersEnabled == that.timersEnabled
        && idleTimeout == that.idleTimeout;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        enabled,
        protocol,
        port,
        host,
        metricCategories,
        pushEnabled,
        pushPort,
        pushHost,
        pushInterval,
        prometheusJob,
        hostsAllowlist,
        timersEnabled,
        idleTimeout);
  }

  public static class Builder {
    private boolean enabled = false;
    private MetricsProtocol protocol = DEFAULT_METRICS_PROTOCOL;
    private int port = DEFAULT_METRICS_PORT;
    private String host = DEFAULT_METRICS_HOST;
    private Set<MetricCategory> metricCategories = DEFAULT_METRIC_CATEGORIES;
    private boolean pushEnabled = false;
    private int pushPort = DEFAULT_METRICS_PUSH_PORT;
    private String pushHost = DEFAULT_METRICS_PUSH_HOST;
    private int pushInterval = 15;
    private String prometheusJob = "besu-client";
    private List<String> hostsAllowlist = Arrays.asList("localhost", "127.0.0.1");
    private boolean timersEnabled = DEFAULT_METRICS_TIMERS_ENABLED;
    private int idleTimeout = DEFAULT_METRICS_IDLE_TIMEOUT_SECONDS;

    private Builder() {}

    public Builder enabled(final boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public Builder protocol(final MetricsProtocol protocol) {
      this.protocol = protocol;
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

    // use hostsAllowlist instead
    @Deprecated
    public Builder hostsWhitelist(final List<String> hostsAllowlist) {
      this.hostsAllowlist = hostsAllowlist;
      return this;
    }

    public Builder hostsAllowlist(final List<String> hostsAllowlist) {
      this.hostsAllowlist = hostsAllowlist;
      return this;
    }

    public Builder timersEnabled(final boolean timersEnabled) {
      this.timersEnabled = timersEnabled;
      return this;
    }

    public Builder idleTimeout(final int idleTimeout) {
      this.idleTimeout = idleTimeout;
      return this;
    }

    public MetricsConfiguration build() {
      return new MetricsConfiguration(
          enabled,
          port,
          protocol,
          host,
          metricCategories,
          pushEnabled,
          pushPort,
          pushHost,
          pushInterval,
          prometheusJob,
          hostsAllowlist,
          timersEnabled,
          idleTimeout);
    }
  }
}
