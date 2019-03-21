description: Lite Network Health Monitor
<!--- END of page meta data -->

# Lite Network Health Monitor

Use the EthStats Lite Network Health Monitor to monitor the health of private networks by displaying real time and 
historical statistics about the network nodes.

The lite version supports in-memory persistence or using Redis to persist a fixed number of blocks (by default,
3000). 

View the Network Health Monitor for the [Ethereum MainNet](https://net.ethstats.io).

!!! note 
    The EthStats Lite Block Explorer is an [Alethio product](https://aleth.io/).
    
    Static local ports 80 and 3000 are used in the example of [running the Lite Network Health Monitor 
    for a Pantheon Node](#running-lite-network-health-monitor-for-a-pantheon-node).  

## Statistics

Statistics displayed by the Network Health Monitor include: 

* Nodes in the network. Metrics for nodes include:
    - Information about the last received block such as block number, 
block hash, transaction count, uncle count, block time and propagation time 
    - Connected peers, whether the node is mining, hash rate, latency, and uptime
* Charts for Block Time, Block Difficulty, Block Gas Limit, Block Uncles, Block Transactions, Block Gas Used, 
Block Propagation Histogram, and Top Miners
* IP based geolocation overview
* Node logs. Node logs display the data sent by a node
* Block history.  Block history provides the ability to go back in time and playback the block propagation
 through the nodes
 
## Components 

The Network Health Monitor consists of: 

* [Server](https://github.com/Alethio/ethstats-network-server). Consumes node data received from the 
client. 

* [Client](https://github.com/Alethio/ethstats-cli). A client must be started for each node in the network.
The client extracts data from the node and sends it to the server

* [Dashboard](https://github.com/Alethio/ethstats-network-dashboard). Dashboard displaying [statistics](#statistics).

## Pre-requisities 

[Docker](https://docs.docker.com/install/)

!!! tip
    The Network Health Monitor has a number of dependencies. Using Docker is the easiest way to demonstrate
    the using the Network Health Monitor with Pantheon. The [EthStats CLI](https://github.com/Alethio/ethstats-cli),
    [EthStats Network Server](https://github.com/Alethio/ethstats-network-server), and [EthStats Network
    Dashboard](https://github.com/Alethio/ethstats-network-dashboard) documentation describes how to install 
    the Network Heath Explorer tools. 

## Running Lite Network Health Monitor for a Pantheon Node

This  example describes how to run the Lite Network Heath Monitor for a single Pantheon node. To run the 
Lite Network Health Monitor for a network of nodes, a [client](#3-client) must be started for each node. 

### 1. Server

Start the server using in-memory persistence: 

1. Clone the server repository: 

    ```bash
    git clone https://github.com/Alethio/ethstats-network-server.git
    ```

2. Change into the `/ethstats-network-server/docker/lite-mode/memory-persistence` directory:
   
    ```bash
    cd ethstats-network-server/docker/lite-mode/memory-persistence
    ```

3. Use the provided `docker-compose` file to start the server: 

    ```bash
    docker-compose up -d
    ```
   
!!! tip
    A `docker-compose` file is also provided in the `ethstats-network-server/docker/lite-mode/redis-persistence`
    directory to run the server using Redis to persist a fixed number of blocks (default is 3000).

### 2. Pantheon 

Start Pantheon in development mode with Websockets enabled:

```bash
docker run --rm -p 8546:8546 pegasyseng/pantheon:latest --miner-enabled --miner-coinbase fe3b557e8fb62b89f4916b721be55ceb828dbd73 --rpc-http-cors-origins="all" --rpc-ws-enabled --network=dev
```

Where `<pantheondata-path>` is the volume to which the node data is saved. 

### 3. Client 

Start the client for the Pantheon node:  

```bash
docker run -d --rm --net host alethio/ethstats-cli --register --account-email <email> --node-name <node_name> --server-url http://localhost:3000 --client-url ws://127.0.0.1:8546
```

Where: 

* `--server-url` specifies [your server](#1-server). The default is the server that consumes data for the Ethereum MainNet.
* `--register` specifies the registration of the Pantheon node is done automatically with the specified `<email>` and `<node_name>`. 
Registering the node is only required the first time the client is started for the node.
* `--client-url` specifies the WebSockets URL for the Pantheon node.    

### 4. Dashboard 

To display the Network Health Monitor dashboard, open [`localhost`](http://localhost) in your browser. 

### Stopping and Cleaning Up Resources

When you've finished running the Network Health Monitor:

1. Stop Pantheon using ++ctrl+c++.  

1. Stop the server and remove containers and volumes: 

    ```bash
    docker-compose down -v
    ```  
   
1. Obtain client container ID: 

    ```bash
    docker ps 
    ```
  
1. Stop the client and remove container: 
   
    ```bash
    docker rm -f <id>
    ```