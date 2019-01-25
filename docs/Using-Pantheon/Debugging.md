description: Frequently asked questions FAQ and answers for troubleshooting Pantheon use
<!--- END of page meta data -->

# Debugging Pantheon

## Command Line Options Not Working as Expected

Characters such as smart quotes and long (em) hyphens won't work in Pantheon options. Ensure that quotes have
not been automatically converted to smart quotes or that double hyphens have not been combined into em hyphens.

## Monitor Node Performance and Connectivity Using the JSON-RPC API

You can monitor node performance using the [`debug_metrics`](../Reference/JSON-RPC-API-Methods.md#debug_metrics)
JSON-RPC API method.

## Monitor Node Performance Using Third-Party Clients

Use the [`--metrics-enabled` option](../Reference/Pantheon-CLI-Syntax.md#metrics-enabled) to enable the [Prometheus](https://prometheus.io/) monitoring and 
alerting service to access Pantheon metrics. You can also visualize the collected data using [Grafana](https://grafana.com/).

While Prometheus is running, it consumes the Pantheon data directly for monitoring. To specify the host and port on which 
Prometheus accesses Pantheon, use the [`--metrics-listen` option](../Reference/Pantheon-CLI-Syntax.md#metrics-listen). 
The default host and port are 127.0.0.1:9545.

You can install other Prometheus components such as the Alert Manager. Additional configuration
 is not required for these components because Prometheus handles and analyzes data directly from the feed.

Here's an example of setting up and running Prometheus with Pantheon:

1. Install the [prometheus main component](https://prometheus.io/download/). On MacOS you can install with [Homebrew](https://brew.sh/): 
 ```bash
 brew install prometheus
 ```

2. Configure Prometheus to poll Pantheon. For example, add the following yaml fragment to the `scrape_configs`
block of the `prometheus.yml` file:
 
    ```yml tab="Example"
      job_name: pantheon-dev
      scrape_interval: 15s
      scrape_timeout: 10s
      metrics_path: /metrics
      scheme: http
      static_configs:
      - targets:
        - localhost:9545
    ```

    !!! note
        The [`--host-whitelist` option](../Reference/Pantheon-CLI-Syntax.md#host-whitelist) defaults to `localhost`.
        If `127.0.0.1` is specified instead of `localhost` in the `prometheus.yml` file, add `127.0.0.1` to the host whitelist
        using [`--host-whitelist`](../Reference/Pantheon-CLI-Syntax.md#host-whitelist) when starting Pantheon. 


3. Start Pantheon with the [`--metrics-enabled` option](../Reference/Pantheon-CLI-Syntax.md#metrics-enabled). To start
 a single node for testing with metrics enabled:

    ```bash tab="Example"
    pantheon --network=dev --miner-enabled --miner-coinbase fe3b557e8fb62b89f4916b721be55ceb828dbd73
    --rpc-http-cors-origins="all" --rpc-http-enabled --metrics-enabled
    ```

4. In another terminal, run Prometheus specifying the `prometheus.yml` file: 

    ```bash tab="Example"
    prometheus --config.file=config.yml 
    ```

5. Open a web browser to `http://localhost:9090` to view the Prometheus graphical interface.

6. Choose **Graph** from the menu bar and click the **Console** tab below.

7. From the **Insert metric at cursor** drop-down, select a metric such as `pantheon_blockchain_difficulty_total` or
`pantheon_blockchain_height` and click **Execute**. The values are displayed below.

    Click the **Graph** tab to view the data as a time-based graph. The query string is displayed below the graph. 
    For example: `{pantheon_blockchain_height{instance="localhost:9545",job="prometheus"}`
