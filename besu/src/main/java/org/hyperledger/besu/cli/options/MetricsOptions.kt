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
package org.hyperledger.besu.cli.options

import com.google.common.base.Preconditions
import org.hyperledger.besu.cli.DefaultCommandValues
import org.hyperledger.besu.cli.util.CommandLineUtils
import org.hyperledger.besu.metrics.BesuMetricCategory
import org.hyperledger.besu.metrics.MetricCategoryRegistryImpl
import org.hyperledger.besu.metrics.MetricsProtocol
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration
import org.hyperledger.besu.plugin.services.metrics.MetricCategory
import picocli.CommandLine
import java.util.stream.Collectors

/** Command line options for configuring metrics.  */ // TODO: implement CLIOption<MetricsConfiguration>
class MetricsOptions private constructor() : CLIOptions<MetricsConfiguration.Builder?> {
    private var metricCategoryRegistry: MetricCategoryRegistryImpl? = null

    /**
     * Returns a MetricsConfiguration.Builder because fields are often overridden from other domains,
     * like using P2P settings as defaults.
     *
     * @return a newly created [MetricsOptions] with default values
     */
    override fun toDomainObject(): MetricsConfiguration.Builder {
        val builder = MetricsConfiguration.builder()
        builder
            .timersEnabled(unstableOptions.timersEnabled)
            .idleTimeout(unstableOptions.idleTimeout)
            .enabled(metricsEnabled)
            .host(metricsHost)
            .port(metricsPort)
            .protocol(metricsProtocol)
            .metricCategories(getMetricCategories())
            .pushEnabled(metricsPushEnabled)
            .pushHost(metricsPushHost)
            .pushPort(metricsPushPort)
            .pushInterval(metricsPushInterval)
            .prometheusJob(metricsPrometheusJob)
        return builder
    }

    override fun getCLIOptions(): List<String> {
        return CommandLineUtils.getCLIOptions(this, MetricsOptions())
    }

    /**
     * Returns whether metrics are enabled.
     *
     * @return true if metrics are enabled, otherwise false
     */
    @CommandLine.Option(
        names = ["--metrics-enabled"],
        description = ["Set to start the metrics exporter (default: \${DEFAULT-VALUE})"]
    )
    var metricsEnabled: Boolean = false
        private set

    /**
     * Returns the metrics protocol.
     *
     * @return the metrics protocol
     */
    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--metrics-protocol"],
        description = ["Metrics protocol, one of PROMETHEUS, OPENTELEMETRY or NONE. (default: \${DEFAULT-VALUE})"]
    )
    var metricsProtocol: MetricsProtocol = MetricsProtocol.PROMETHEUS
        private set

    /**
     * Returns the metrics host.
     *
     * @return the metrics host
     */
    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--metrics-host"],
        paramLabel = DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP,
        description = ["Host for the metrics exporter to listen on (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    var metricsHost: String = MetricsConfiguration.DEFAULT_METRICS_HOST
        private set

    /**
     * Returns the metrics port.
     *
     * @return the metrics port
     */
    @CommandLine.Option(
        names = ["--metrics-port"],
        paramLabel = DefaultCommandValues.MANDATORY_PORT_FORMAT_HELP,
        description = ["Port for the metrics exporter to listen on (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    var metricsPort: Int = MetricsConfiguration.DEFAULT_METRICS_PORT
        private set

    @CommandLine.Option(
        names = ["--metrics-category", "--metrics-categories"],
        paramLabel = "<category name>",
        split = ",",
        arity = "1..*",
        description = ["Comma separated list of categories to track metrics for (default: \${DEFAULT-VALUE})"]
    )
    private var metricCategories: Set<String> =
        BesuMetricCategory.DEFAULT_METRIC_CATEGORIES.stream().map<String> { obj: MetricCategory -> obj.name }
            .collect(Collectors.toUnmodifiableSet<String>())

    /**
     * Returns whether metrics push is enabled.
     *
     * @return true if metrics push is enabled, otherwise false
     */
    @CommandLine.Option(
        names = ["--metrics-push-enabled"],
        description = ["Enable the metrics push gateway integration (default: \${DEFAULT-VALUE})"]
    )
    var metricsPushEnabled: Boolean = false
        private set

    /**
     * Returns the metrics push host.
     *
     * @return the metrics push host
     */
    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--metrics-push-host"],
        paramLabel = DefaultCommandValues.MANDATORY_HOST_FORMAT_HELP,
        description = ["Host of the Prometheus Push Gateway for push mode (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    var metricsPushHost: String = MetricsConfiguration.DEFAULT_METRICS_PUSH_HOST
        private set

    /**
     * Returns the metrics push port.
     *
     * @return the metrics push port
     */
    @CommandLine.Option(
        names = ["--metrics-push-port"],
        paramLabel = DefaultCommandValues.MANDATORY_PORT_FORMAT_HELP,
        description = ["Port of the Prometheus Push Gateway for push mode (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    var metricsPushPort: Int = MetricsConfiguration.DEFAULT_METRICS_PUSH_PORT
        private set

    /**
     * Returns the metrics push interval.
     *
     * @return the metrics push interval
     */
    @CommandLine.Option(
        names = ["--metrics-push-interval"],
        paramLabel = DefaultCommandValues.MANDATORY_INTEGER_FORMAT_HELP,
        description = ["Interval in seconds to push metrics when in push mode (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    var metricsPushInterval: Int = 15
        private set

    /**
     * Returns the metrics prometheus job.
     *
     * @return the metrics prometheus job
     */
    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--metrics-push-prometheus-job"],
        description = ["Job name to use when in push mode (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    var metricsPrometheusJob: String = "besu-client"
        private set

    /**
     * Returns the metric categories.
     *
     * @return the metric categories
     */
    fun getMetricCategories(): Set<MetricCategory> {
        Preconditions.checkState(
            metricCategoryRegistry != null, "Set metricCategoryRegistry before calling this method"
        )

        val list =
            metricCategories.stream().map { name: String? -> metricCategoryRegistry!!.getMetricCategory(name) }.toList()

        return java.util.Set.copyOf(list)
    }

    /**
     * Perform final validation after all the options, and the metric category registry, have been set
     *
     * @param commandLine the command line
     */
    fun validate(commandLine: CommandLine) {
        Preconditions.checkState(
            metricCategoryRegistry != null, "Set metricCategoryRegistry before calling this method"
        )
        val unknownCategories =
            metricCategories.stream()
                .filter { category: String? -> !metricCategoryRegistry!!.containsMetricCategory(category) }
                .toList()
        if (!unknownCategories.isEmpty()) {
            throw CommandLine.ParameterException(
                commandLine, "--metrics-categories contains unknown categories: $unknownCategories"
            )
        }
    }

    /**
     * Set the metric category registry in order to support verification and conversion
     *
     * @param metricCategoryRegistry the metric category registry
     */
    fun setMetricCategoryRegistry(metricCategoryRegistry: MetricCategoryRegistryImpl?) {
        this.metricCategoryRegistry = metricCategoryRegistry
    }

    @CommandLine.ArgGroup(validate = false)
    private val unstableOptions = Unstable()

    internal class Unstable {
        @CommandLine.Option(
            names = [TIMERS_ENABLED_FLAG],
            hidden = true,
            description = ["Whether to enable timer metrics (default: \${DEFAULT-VALUE})."]
        )
        var timersEnabled: Boolean = MetricsConfiguration.DEFAULT_METRICS_TIMERS_ENABLED

        @CommandLine.Option(
            hidden = true,
            names = [IDLE_TIMEOUT_FLAG],
            paramLabel = "<INTEGER>",
            description = ["Timeout for metrics TCP connections, in seconds (default: \${DEFAULT-VALUE})"],
            arity = "1"
        )
        var idleTimeout: Int = MetricsConfiguration.DEFAULT_METRICS_IDLE_TIMEOUT_SECONDS

        companion object {
            private const val TIMERS_ENABLED_FLAG = "--Xmetrics-timers-enabled"
            private const val IDLE_TIMEOUT_FLAG = "--Xmetrics-idle-timeout"
        }
    }

    companion object {
        // TODO: why do we need to be able to reverse this?
        /**
         * Returns a newly created [MetricsOptions] reversed from the supplied MetricsConfiguration
         *
         * @param config the metrics configuration
         * @return a newly created [MetricsOptions] reversed from the supplied MetricsConfiguration
         */
        @JvmStatic
        fun fromConfiguration(config: MetricsConfiguration): MetricsOptions {
            val metricsOptions = create()
            metricsOptions.unstableOptions.timersEnabled = config.isTimersEnabled
            metricsOptions.unstableOptions.idleTimeout = config.idleTimeout
            metricsOptions.metricsEnabled = config.isEnabled
            metricsOptions.metricsHost = config.host
            metricsOptions.metricsPort = config.port
            metricsOptions.metricsProtocol = config.protocol
            metricsOptions.metricCategories =
                config.metricCategories.stream()
                    .map<String> { obj: MetricCategory -> obj.name }
                    .collect(Collectors.toUnmodifiableSet<String>())
            metricsOptions.metricsPrometheusJob = config.prometheusJob
            metricsOptions.metricsPushEnabled = config.isPushEnabled
            metricsOptions.metricsPushHost = config.pushHost
            metricsOptions.metricsPushPort = config.pushPort
            metricsOptions.metricsPushInterval = config.pushInterval

            return metricsOptions
        }

        /**
         * Returns a newly created [MetricsOptions] with default values.
         *
         * @return new instance
         */
        @JvmStatic
        fun create(): MetricsOptions {
            return MetricsOptions()
        }
    }
}
