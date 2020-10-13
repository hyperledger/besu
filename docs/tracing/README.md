# Tracing

Hyperledger Besu integrates with the [open-telemetry](https://open-telemetry.io) project to integrate tracing reporting.

This allows to report all JSON-RPC traffic as traces.

To try out this example, start the Open Telemetry Collector and the Zipkin service with:

`$> docker-compose up`

Start besu with:

`$> ./gradlew run --args="--network=dev --rpc-http-enabled"`

Try interacting with the JSON-RPC API. Here is a simple example using cURL:

`$> curl -X POST --data '{"jsonrpc":"2.0","method":"web3_clientVersion","params":[],"id":53}' http://localhost:8545`

Open the Zipkin UI by browsing to http://localhost:9411/

You will be able to see the detail of your traces.
