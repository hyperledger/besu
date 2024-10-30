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

/** The Metrics configuration. */
public class MetricsConfiguration {

  /** The constant DEFAULT_METRICS_HOST. */
  public static final String DEFAULT_METRICS_HOST = "127.0.0.1";

  /** The constant DEFAULT_METRICS_PORT. */
  public static final int DEFAULT_METRICS_PORT = 9545;

  private static final MetricsProtocol DEFAULT_METRICS_PROTOCOL = MetricsProtocol.PROMETHEUS;

  /** The constant DEFAULT_METRICS_PUSH_HOST. */
  public static final String DEFAULT_METRICS_PUSH_HOST = "127.0.0.1";

  /** The constant DEFAULT_METRICS_PUSH_PORT. */
  public static final int DEFAULT_METRICS_PUSH_PORT = 9001;

  /** The constant DEFAULT_METRICS_TIMERS_ENABLED. */
  public static final Boolean DEFAULT_METRICS_TIMERS_ENABLED = true;

  /** The constant DEFAULT_METRICS_IDLE_TIMEOUT_SECONDS. */
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

  /**
   * Builder.
   *
   * @return the builder
   */
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

  /**
   * Is enabled.
   *
   * @return the boolean
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Gets protocol.
   *
   * @return the protocol
   */
  public MetricsProtocol getProtocol() {
    return protocol;
  }

  /**
   * Gets host.
   *
   * @return the host
   */
  public String getHost() {
    return host;
  }

  /**
   * Gets port.
   *
   * @return the port
   */
  public int getPort() {
    return port;
  }

  /**
   * Gets actual port.
   *
   * @return the actual port
   */
  public int getActualPort() {
    return actualPort;
  }

  /**
   * Sets actual port.
   *
   * @param actualPort the actual port
   */
  void setActualPort(final int actualPort) {
    this.actualPort = actualPort;
  }

  /**
   * Gets metric categories.
   *
   * @return the metric categories
   */
  public Set<MetricCategory> getMetricCategories() {
    return metricCategories;
  }

  /**
   * Gets push port.
   *
   * @return the push port
   */
  public int getPushPort() {
    return pushPort;
  }

  /**
   * Gets push host.
   *
   * @return the push host
   */
  public String getPushHost() {
    return pushHost;
  }

  /**
   * Is push enabled boolean.
   *
   * @return the boolean
   */
  public boolean isPushEnabled() {
    return pushEnabled;
  }

  /**
   * Gets push interval.
   *
   * @return the push interval
   */
  public int getPushInterval() {
    return pushInterval;
  }

  /**
   * Gets prometheus job.
   *
   * @return the prometheus job
   */
  public String getPrometheusJob() {
    return prometheusJob;
  }

  /**
   * Gets hosts whitelist.
   *
   * @return the hosts whitelist
   */
  // use getHostsAllowlist instead
  @Deprecated
  Collection<String> getHostsWhitelist() {
    return Collections.unmodifiableCollection(this.hostsAllowlist);
  }

  /**
   * Gets hosts allowlist.
   *
   * @return the hosts allowlist
   */
  Collection<String> getHostsAllowlist() {
    return Collections.unmodifiableCollection(this.hostsAllowlist);
  }

  /**
   * Is timers enabled.
   *
   * @return the boolean
   */
  public boolean isTimersEnabled() {
    return timersEnabled;
  }

  /**
   * Gets idle timeout.
   *
   * @return the idle timeout
   */
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

  /** The type Builder. */
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

    /**
     * Enabled.
     *
     * @param enabled the enabled
     * @return the builder
     */
    public Builder enabled(final boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    /**
     * Protocol.
     *
     * @param protocol the protocol
     * @return the builder
     */
    public Builder protocol(final MetricsProtocol protocol) {
      this.protocol = protocol;
      return this;
    }

    /**
     * Port.
     *
     * @param port the port
     * @return the builder
     */
    public Builder port(final int port) {
      this.port = port;
      return this;
    }

    /**
     * Host.
     *
     * @param host the host
     * @return the builder
     */
    public Builder host(final String host) {
      this.host = host;
      return this;
    }

    /**
     * Metric categories.
     *
     * @param metricCategories the metric categories
     * @return the builder
     */
    public Builder metricCategories(final Set<MetricCategory> metricCategories) {
      this.metricCategories = metricCategories;
      return this;
    }

    /**
     * Push enabled.
     *
     * @param pushEnabled the push enabled
     * @return the builder
     */
    public Builder pushEnabled(final boolean pushEnabled) {
      this.pushEnabled = pushEnabled;
      return this;
    }

    /**
     * Push port.
     *
     * @param pushPort the push port
     * @return the builder
     */
    public Builder pushPort(final int pushPort) {
      this.pushPort = pushPort;
      return this;
    }

    /**
     * Push host.
     *
     * @param pushHost the push host
     * @return the builder
     */
    public Builder pushHost(final String pushHost) {
      this.pushHost = pushHost;
      return this;
    }

    /**
     * Push interval.
     *
     * @param pushInterval the push interval
     * @return the builder
     */
    public Builder pushInterval(final int pushInterval) {
      this.pushInterval = pushInterval;
      return this;
    }

    /**
     * Prometheus job.
     *
     * @param prometheusJob the prometheus job
     * @return the builder
     */
    public Builder prometheusJob(final String prometheusJob) {
      this.prometheusJob = prometheusJob;
      return this;
    }

    /**
     * Hosts whitelist.
     *
     * @param hostsAllowlist the hosts allowlist
     * @return the builder
     */
    // use hostsAllowlist instead
    @Deprecated
    public Builder hostsWhitelist(final List<String> hostsAllowlist) {
      this.hostsAllowlist = hostsAllowlist;
      return this;
    }

    /**
     * Hosts allowlist.
     *
     * @param hostsAllowlist the hosts allowlist
     * @return the builder
     */
    public Builder hostsAllowlist(final List<String> hostsAllowlist) {
      this.hostsAllowlist = hostsAllowlist;
      return this;
    }

    /**
     * Timers enabled.
     *
     * @param timersEnabled the timers enabled
     * @return the builder
     */
    public Builder timersEnabled(final boolean timersEnabled) {
      this.timersEnabled = timersEnabled;
      return this;
    }

    /**
     * Idle timeout.
     *
     * @param idleTimeout the idle timeout
     * @return the builder
     */
    public Builder idleTimeout(final int idleTimeout) {
      this.idleTimeout = idleTimeout;
      return this;
    }

    /**
     * Build metrics configuration.
     *
     * @return the metrics configuration
     */
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
