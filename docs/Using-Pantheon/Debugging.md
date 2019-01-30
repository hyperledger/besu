description: Frequently asked questions FAQ and answers for troubleshooting Pantheon use
<!--- END of page meta data -->

# Debugging Pantheon

## Command Line Options Not Working as Expected

Characters such as smart quotes and long (em) hyphens won't work in Pantheon options. Ensure that quotes have
not been automatically converted to smart quotes or that double hyphens have not been combined into em hyphens.

## Monitor Node Performance and Connectivity Using the JSON-RPC API

You can monitor node performance using the [`debug_metrics`](../Reference/JSON-RPC-API-Methods.md#debug_metrics)
JSON-RPC API method.

## Monitor Node Performance Using Prometheus

Use the [`--metrics-enabled` option](../Reference/Pantheon-CLI-Syntax.md#metrics-enabled) to enable the [Prometheus](https://prometheus.io/) monitoring and 
alerting service to access Pantheon metrics. You can also visualize the collected data using [Grafana](https://grafana.com/).

To specify the host and port on which Prometheus accesses Pantheon, use the [`--metrics-host`](../Reference/Pantheon-CLI-Syntax.md#metrics-host) and 
[`--metrics-port`](../Reference/Pantheon-CLI-Syntax.md#metrics-port) options. 
The default host and port are 127.0.0.1 and 9545.

You can install other Prometheus components such as the Alert Manager. Additional configuration
 is not required for these components because Prometheus handles and analyzes data directly from the feed.

To use Prometheus with Pantheon, install the [prometheus main component](https://prometheus.io/download/). On MacOS, install with [Homebrew](https://formulae.brew.sh/formula/prometheus): 

 ```
 brew install prometheus
```

###  Setting up and Running Prometheus with Pantheon

To configure Prometheus and run with Pantheon: 

1. Configure Prometheus to poll Pantheon. For example, add the following yaml fragment to the `scrape_configs`
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


1. Start Pantheon with the [`--metrics-enabled` option](../Reference/Pantheon-CLI-Syntax.md#metrics-enabled). To start
 a single node for testing with metrics enabled:

    ```bash tab="Example"
    pantheon --network=dev --miner-enabled --miner-coinbase fe3b557e8fb62b89f4916b721be55ceb828dbd73
    --rpc-http-cors-origins="all" --rpc-http-enabled --metrics-enabled
    ```

1. In another terminal, run Prometheus specifying the `prometheus.yml` file: 

    ```bash tab="Example"
    prometheus --config.file=config.yml 
    ```

1. Open a web browser to `http://localhost:9090` to view the Prometheus graphical interface.

1. Choose **Graph** from the menu bar and click the **Console** tab below.

1. From the **Insert metric at cursor** drop-down, select a metric such as `pantheon_blockchain_difficulty_total` or
`pantheon_blockchain_height` and click **Execute**. The values are displayed below.

    Click the **Graph** tab to view the data as a time-based graph. The query string is displayed below the graph. 
    For example: `{pantheon_blockchain_height{instance="localhost:9545",job="prometheus"}`

### Running Prometheus with Pantheon in Push Mode 

The [`--metrics-enabled`](../Reference/Pantheon-CLI-Syntax.md#metrics-enabled) option enables Prometheus polling 
Pantheon but sometimes metrics are hard to poll (for example, when running inside Docker containers with varying IP addresses). 
The [`--metrics-push-enabled`](../Reference/Pantheon-CLI-Syntax.md#metrics-push-enabled) option enables Pantheon 
to push metrics to a [Prometheus Pushgateway](https://github.com/prometheus/pushgateway).   

To configure Prometheus and run with Pantheon pushing to a push gateway: 

1. Configure Prometheus to read from a push gateway. For example, add the following yaml fragment to the `scrape_configs`
   block of the `prometheus.yml` file:
    
       ```yml tab="Example"
        - job_name: push-gateway
          metrics_path: /metrics
          scheme: http
          static_configs:
          - targets:
            - localhost:9091
       ```
   
    !!! note
        The [`--host-whitelist` option](../Reference/Pantheon-CLI-Syntax.md#host-whitelist) defaults to `localhost`.
        If `127.0.0.1` is specified instead of `localhost` in the `prometheus.yml` file, add `127.0.0.1` to the host whitelist
        using [`--host-whitelist`](../Reference/Pantheon-CLI-Syntax.md#host-whitelist) when starting Pantheon. 

1. Start the push gateway. The push gateway can be deployed using the Docker image: 

    ```bash tab="Example"
    docker pull prom/pushgateway
    docker run -d -p 9091:9091 prom/pushgateway
    ```

1. Start Pantheon specifying the `--metrics-push-enabled` option and port of the push gateway: 

    ```bash tab="Example"
    pantheon --network=dev --miner-enabled --miner-coinbase fe3b557e8fb62b89f4916b721be55ceb828dbd73 --rpc-http-cors-origins="all" --rpc-http-enabled --metrics-push-enabled --metrics-push-port=9091 --metrics-push-host=127.0.0.1
    ```

1. In another terminal, run Prometheus specifying the `prometheus.yml` file: 
   
    ```bash tab="Example"
    prometheus --config.file=config.yml 
    ```

1. View the Prometheus graphical interface as described in [Setting up and Running Prometheus with Pantheon](#setting-up-and-running-prometheus-with-pantheon).
