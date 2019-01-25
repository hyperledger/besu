description: Run Pantheon using the official docker image
<!--- END of page meta data -->

# Running Pantheon from Docker Image

A Docker image is provided to run a Pantheon node in a Docker container. 

Use this Docker image to run a single Pantheon node without installing Pantheon. 

!!!caution
    If you have been running a node using the v0.8.3 Docker image, the node was not saving data to the 
    specified [data directory](#data-directory), or referring to the custom [configuration file](#custom-configuration-file)
    or [genesis file](#custom-genesis-file). 
   
    To recover the node key and data directory from the Docker container:
    
    `docker cp <container>:/opt/pantheon/key <destination_file>`
    
    `docker cp <container>:/opt/pantheon/database <destination_directory>` 
   
    Where `container` is the name or ID of the Docker container containing the Pantheon node. 
   
    The container can be running or stopped when you copy the key and data directory. If your node was 
    fully synchronized to MainNet, the data directory will be ~2TB.  
   
    When restarting your node with the v0.8.4 Docker image:

    * Save the node key in the [`key` file](../Configuring-Pantheon/Node-Keys.md#node-private-key) in the data 
    directory or specify the location using the [`--node-private-key` option](../Configuring-Pantheon/Node-Keys.md#specifying-a-custom-node-private-key-file).  
    
    * Specify the `<destination_directory` as a [volume for the data directory](#data-directory). 

## Prerequisites

To run Pantheon from the Docker image, you must have [Docker](https://docs.docker.com/install/) installed.  

## Quickstart

To run a Pantheon node in a container connected to the Ethereum mainnet: 

```bash
docker run pegasyseng/pantheon:latest
```

## Command Line Options 
 
!!!attention
    You cannot use the following Pantheon command line options when running Pantheon from the Docker image:
    
    * [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path), see [Persisting Data](#persisting-data)
    * [`--config-file`](../Reference/Pantheon-CLI-Syntax.md#config), see [Custom Configuration File](#custom-configuration-file)
    * [`--genesis-file`](../Reference/Pantheon-CLI-Syntax.md#genesis-file), see [Custom Genesis File](#custom-genesis-file).
    * [`--rpc-http-host`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-host) and [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port),
    [`--p2p-host`](../Reference/Pantheon-CLI-Syntax.md#p2p-host) and [`--p2p-port`](../Reference/Pantheon-CLI-Syntax.md#p2p-port),
    [`--rpc-ws-host`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-host) and [`--rpc-ws-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-port),
    see [Exposing Ports](#exposing-ports)
    
    All other [Pantheon command line options](/Reference/Pantheon-CLI-Syntax) work in the same way as when Pantheon is installed locally.

### Data Directory 

Specify a Docker volume for the data directory. This is the equivalent of specifying the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option. 

To run Pantheon specifying a volume for the data directory: 

```bash
docker run --mount type=bind,source=/<pantheondata-path>,target=/var/lib/pantheon pegasyseng/pantheon:latest

``` 

Where `<pantheondata-path>` is the volume to which the data is saved.  

### Custom Configuration File 

Specify a custom configuration file to provide a file containing key/value pairs for command line options. This is the equivalent of specifying the [`--config-file`](../Reference/Pantheon-CLI-Syntax.md#config-file) option. 

To run Pantheon specifying a custom configuration file: 
```bash
docker run --mount type=bind,source=/<path/myconf.toml>,target=/etc/pantheon/pantheon.conf pegasyseng/pantheon:latest

```

Where `myconf.toml` is your custom configuration file and `path` is the absolute path to the file.
!!!example
    ```bash
    docker run --mount type=bind,source=/Users/username/pantheon/myconf.toml,target=/etc/pantheon/pantheon.conf pegasyseng/pantheon:latest
    ```

### Custom Genesis File 

Specify a custom genesis file to configure the blockchain. This is equivalent to specifying the `--genesis-file` option.

To run Pantheon specifying a custom genesis file: 
```bash
docker run --mount type=bind,source=</path/mygenesis.json>,target=/etc/pantheon/genesis.json pegasyseng/pantheon:latest
```

Where `mygenesis.json` is your custom configuration file and `path` is the absolute path to the file.

!!!example
    ```bash
    docker run --mount type=bind,source=/Users/username/pantheon/mygenesis.json,target=/etc/pantheon/genesis.json pegasyseng/pantheon:latest
    ```

### Exposing Ports

Expose ports for P2P peer discovery, JSON-RPC service, and WebSockets. This is required to use the 
defaults ports or specify different ports (the equivalent of specifying the [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port), 
[`--p2p-port`](../Reference/Pantheon-CLI-Syntax.md#p2p-port), [`--rpc-ws-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-port) options).

To run Pantheon exposing local ports for access: 
```bash
$ docker run -p <localportJSON-RPC>:8545 -p <localportWS>:8546 -p <localportP2P>:30303 pegasyseng/pantheon:latest --rpc-http-enabled --rpc-ws-enabled
```

!!!example
    To enable RPC calls to http://127.0.0.1:8545 and P2P discovery on http://127.0.0.1:13001:
    ```bash
    docker run -p 8545:8545 -p 13001:30303 pegasyseng/pantheon:latest --rpc-http-enabled
    ```
 
## Starting Pantheon 

### Run a Node on Ethereum Mainnet 

To run a node on the Ethereum mainnet: 

```bash
docker run -p 30303:30303 --mount type=bind,source=/<myvolume/pantheon>,target=/var/lib/pantheon pegasyseng/pantheon:latest
```

To run a node on mainnet with the HTTP JSON-RPC service enabled: 
```bash
docker run -p 8545:8545 -p 30303:30303 --mount type=bind,source=/<myvolume/pantheon>,target=/var/lib/pantheon pegasyseng/pantheon:latest --rpc-http-enabled
```

## Run a Node on Ropsten Testnet 

Save a local copy of the [Ropsten genesis file](https://github.com/PegaSysEng/pantheon/blob/master/config/src/main/resources/ropsten.json). 

To run a node on Ropsten: 
```bash
docker run -p 30303:30303 --mount type=bind,source=/<myvolume/pantheon/ropsten>,target=/var/lib/pantheon --network=ropsten
```

## Run a Node on Rinkeby Testnet 

To run a node on Rinkeby: 
```bash
docker run -p 30303:30303 --mount type=bind,source=/<myvolume/pantheon/rinkeby>,target=/var/lib/pantheon pegasyseng/pantheon:latest --network=rinkeby
```

## Run a Node for Testing 

To run a node that mines blocks at a rate suitable for testing purposes with WebSockets enabled: 
```bash
docker run -p 8546:8546 --mount type=bind,source=/<myvolume/pantheon/testnode>,target=/var/lib/pantheon pegasyseng/pantheon:latest --miner-enabled --miner-coinbase fe3b557e8fb62b89f4916b721be55ceb828dbd73 --rpc-http-cors-origins "all" --rpc-ws-enabled --network=dev
```

## Stopping Pantheon and Cleaning up Resources

When you're done running nodes, you can shut down the node container without deleting resources. Alternatively, you can delete the container (after stopping it) and its associated volume. Run `docker container ls` and `docker volume ls` to obtain the container and volume names. Then run the following commands:

To stop a container:
```bash
docker stop <container-name>
```

To delete a container:
```bash
docker rm <container-name>
```

To delete a container volume (optional):
```bash
docker volume rm <volume-name>
```