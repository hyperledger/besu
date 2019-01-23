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

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import com.google.common.collect.Lists;

public class MetricsConfiguration {
  private static final String DEFAULT_METRICS_HOST = "127.0.0.1";
  public static final int DEFAULT_METRICS_PORT = 9545;

  public static final String MODE_PUSH_GATEWAY = "push";
  public static final String MODE_SERVER_PULL = "pull";

  private boolean enabled;
  private int port;
  private String host;
  private String mode;
  private int pushInterval;
  private String prometheusJob;
  private Collection<String> hostsWhitelist = Collections.singletonList("localhost");

  public static MetricsConfiguration createDefault() {
    final MetricsConfiguration metricsConfiguration = new MetricsConfiguration();
    metricsConfiguration.setEnabled(false);
    metricsConfiguration.setPort(DEFAULT_METRICS_PORT);
    metricsConfiguration.setHost(DEFAULT_METRICS_HOST);
    metricsConfiguration.setMode(MODE_SERVER_PULL);
    metricsConfiguration.setPushInterval(15);
    metricsConfiguration.setPrometheusJob("pantheon-client");

    return metricsConfiguration;
  }

  private MetricsConfiguration() {}

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public int getPort() {
    return port;
  }

  public void setPort(final int port) {
    this.port = port;
  }

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(final String mode) {
    this.mode = mode;
  }

  public int getPushInterval() {
    return pushInterval;
  }

  public void setPushInterval(final int pushInterval) {
    this.pushInterval = pushInterval;
  }

  public String getPrometheusJob() {
    return prometheusJob;
  }

  public void setPrometheusJob(final String prometheusJob) {
    this.prometheusJob = prometheusJob;
  }

  Collection<String> getHostsWhitelist() {
    return Collections.unmodifiableCollection(this.hostsWhitelist);
  }

  public void setHostsWhitelist(final Collection<String> hostsWhitelist) {
    this.hostsWhitelist = hostsWhitelist;
  }

  @Override
  public String toString() {
    return "MetricsConfiguration{"
        + "enabled="
        + enabled
        + ", port="
        + port
        + ", host='"
        + host
        + '\''
        + ", hostsWhitelist="
        + hostsWhitelist
        + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final MetricsConfiguration that = (MetricsConfiguration) o;
    return enabled == that.enabled
        && port == that.port
        && Objects.equals(host, that.host)
        && com.google.common.base.Objects.equal(
            Lists.newArrayList(hostsWhitelist), Lists.newArrayList(that.hostsWhitelist));
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, port, host, hostsWhitelist);
  }
}
