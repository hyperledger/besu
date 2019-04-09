description: Run Pantheon using the official docker image
<!--- END of page meta data -->

# Running Pantheon from Docker Image

A Docker image is provided to run a Pantheon node in a Docker container. 

Use this Docker image to run a single Pantheon node without installing Pantheon. 

## Prerequisites

To run Pantheon from the Docker image, you must have [Docker](https://docs.docker.com/install/) installed.  

## Quickstart

To run a Pantheon node in a container connected to the Ethereum mainnet: 

```bash tab="latest"
docker run pegasyseng/pantheon:latest
```

```bash tab="1.0"
docker run pegasyseng/pantheon:1.0
```

!!! note
    `latest` runs the latest cached version. To pull the latest version, use `docker pull pegasyseng/pantheon:latest`. 
 
## Command Line Options 
 
!!!note
    You cannot use the following Pantheon command line options when running Pantheon from the Docker image:
    
    * [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path), see [Data Directory](#data-directory)
    * [`--config-file`](../Reference/Pantheon-CLI-Syntax.md#config), see [Custom Configuration File](#custom-configuration-file)
    * [`--genesis-file`](../Reference/Pantheon-CLI-Syntax.md#genesis-file), see [Custom Genesis File](#custom-genesis-file).
    * [`--permissions-accounts-config-file`](../Reference/Pantheon-CLI-Syntax.md#permissions-accounts-config-file)
    and [`--permissions-nodes-config-file`](../Reference/Pantheon-CLI-Syntax.md#permissions-nodes-config-file), 
    see [Permissions Configuration File](#permissions-configuration-file). 
    * [`--privacy-public-key-file`](../Reference/Pantheon-CLI-Syntax.md#privacy-public-key-file), see [Privacy Public Key File](#privacy-public-key-file).
    * [`--rpc-http-authentication-credentials-file`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-authentication-credentials-file) and
      [`--rpc-ws-authentication-credentials-file`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-authentication-credentials-file), see [Credentials Files](#credentials-files).
    * [`--node-private-key-file`](../Reference/Pantheon-CLI-Syntax.md#node-private-key-file). When running from the Docker image, 
    Pantheon always uses the key file in the [data directory](#data-directory). 
    * Host and port options, see [Exposing Ports](#exposing-ports). Host and port options are: 
        - [`--rpc-http-host`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-host) and [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port)
        - [`--metrics-host`](../Reference/Pantheon-CLI-Syntax.md#metrics-host) and [`--metrics-port`](../Reference/Pantheon-CLI-Syntax.md#metrics-port)
        - [`--metrics-push-host`](../Reference/Pantheon-CLI-Syntax.md#metrics-push-host) and [`--metrics-push-port`](../Reference/Pantheon-CLI-Syntax.md#metrics-push-port)
        - [`--p2p-host`](../Reference/Pantheon-CLI-Syntax.md#p2p-host) and [`--p2p-port`](../Reference/Pantheon-CLI-Syntax.md#p2p-port)
        - [`--rpc-ws-host`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-host) and [`--rpc-ws-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-port)
    
    All other [Pantheon command line options](/Reference/Pantheon-CLI-Syntax) work in the same way as when Pantheon is installed locally.
 
## Starting Pantheon 

### Run a Node for Testing 

To run a node that mines blocks at a rate suitable for testing purposes with WebSockets enabled: 
```bash
docker run -p 8546:8546 --mount type=bind,source=/<myvolume/pantheon/testnode>,target=/var/lib/pantheon pegasyseng/pantheon:latest --miner-enabled --miner-coinbase fe3b557e8fb62b89f4916b721be55ceb828dbd73 --rpc-http-cors-origins="all" --rpc-ws-enabled --network=dev
```

### Run a Node on Rinkeby Testnet 

To run a node on Rinkeby: 
```bash
docker run -p 30303:30303 --mount type=bind,source=/<myvolume/pantheon/rinkeby>,target=/var/lib/pantheon pegasyseng/pantheon:latest --network=rinkeby
```

### Run a Node on Ethereum Mainnet 

To run a node on the Ethereum mainnet: 

```bash
docker run -p 30303:30303 --mount type=bind,source=/<myvolume/pantheon>,target=/var/lib/pantheon pegasyseng/pantheon:latest
```

To run a node on mainnet with the HTTP JSON-RPC service enabled: 
```bash
docker run -p 8545:8545 -p 30303:30303 --mount type=bind,source=/<myvolume/pantheon>,target=/var/lib/pantheon pegasyseng/pantheon:latest --rpc-http-enabled
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

## Data Directory 

Specify a Docker volume for the data directory. This is the equivalent of specifying the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option. 

To run Pantheon specifying a volume for the data directory: 

```bash
docker run --mount type=bind,source=/<pantheondata-path>,target=/var/lib/pantheon pegasyseng/pantheon:latest

``` 

Where `<pantheondata-path>` is the volume to which the data is saved.  

## Custom Configuration File 

Specify a [custom configuration file](../Configuring-Pantheon/Using-Configuration-File.md) to provide a file containing key/value pairs for command line options. This is the equivalent of specifying the [`--config-file`](../Reference/Pantheon-CLI-Syntax.md#config-file) option. 

To run Pantheon specifying a custom configuration file: 
```bash
docker run --mount type=bind,source=/<path/myconf.toml>,target=/etc/pantheon/pantheon.conf pegasyseng/pantheon:latest

```

Where `myconf.toml` is your custom configuration file and `path` is the absolute path to the file.
!!!example
    ```bash
    docker run --mount type=bind,source=/Users/username/pantheon/myconf.toml,target=/etc/pantheon/pantheon.conf pegasyseng/pantheon:latest
    ```

## Custom Genesis File 

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

## Permissions Configuration File 

Specify a permissions configuration file. This is equivalent to specifying the `--permissions-accounts-config-file` 
or the `--permissions-nodes-config-file` option.

!!! note 
    When using Docker, the accounts and nodes permissions must be contained in the same [permissions file](../Permissions/Local-Permissioning.md#permissions-configuration-file). 

To run Pantheon specifying a permissions configuration file: 
```bash
docker run --mount type=bind,source=</path/mypermissions.toml>,target=/etc/pantheon/permissions_config.toml pegasyseng/pantheon:latest
```

Where `mypermissions.toml` is your custom configuration file and `path` is the absolute path to the file.

!!!example
    ```bash
    docker run --mount type=bind,source=/Users/username/pantheon/mypermissions.toml,target=/etc/pantheon/permissions_config.toml pegasyseng/pantheon:latest
    ```

## Privacy Public Key File

Specify a file containing the public key for the enclave. This is equivalent to specifying the `--privacy-public-key-file` option.     

To run Pantheon specifying a privacy public key file: 
```bash
docker run --mount type=bind,source=</path/myprivacypublickeyfile>,target=/etc/pantheon/privacy_public_key pegasyseng/pantheon:latest
```

Where `myprivacypublickeyfile` is the file containing the public key and `path` is the absolute path to the file. 

!!!example
    ```bash
    docker run --mount type=bind,source=/Users/username/pantheon/keyfile,target=/etc/pantheon/privacy_public_key pegasyseng/pantheon:latest
    ```
    
!!!note
    Privacy is under development and will be available in v1.1.

## Credentials Files 

Specify a [credentials file](../JSON-RPC-API/Authentication.md#credentials-file) for JSON-RPC API [authentication](../JSON-RPC-API/Authentication.md).

To run Pantheon specifying a credentials file for HTTP JSON-RPC: 
```bash
docker run --mount type=bind,source=</path/myauthconfig.toml>,target=/etc/pantheon/rpc_http_auth_config.toml pegasyseng/pantheon:latest
```

To run Pantheon specifying a credentials file for WebSockets JSON-RPC: 
```bash
docker run --mount type=bind,source=</path/myauthconfig.toml>,target=/etc/pantheon/rpc_ws_auth_config.toml pegasyseng/pantheon:latest
```

Where `myauthconfig.toml` is the credentials file and `path` is the absolute path to the file. 

!!! example
    ```bash tab="HTTP"
    docker run --mount type=bind,source=/Users/username/pantheon/myauthconfig.toml,target=/etc/pantheon/rpc_http_auth_config.toml pegasyseng/pantheon:latest
    ```
    
    ```bash tab="WS"
        docker run --mount type=bind,source=/Users/username/pantheon/myauthconfig.toml,target=/etc/pantheon/rpc_ws_auth_config.toml pegasyseng/pantheon:latest
    ```
    

## Exposing Ports

Expose ports for P2P peer discovery, metrics, and HTTP and WebSockets JSON-RPC. This is required to use the 
defaults ports or specify different ports (the equivalent of specifying the [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port), 
[`--p2p-port`](../Reference/Pantheon-CLI-Syntax.md#p2p-port), [`--rpc-ws-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-port), 
[`--metrics-port`](../Reference/Pantheon-CLI-Syntax.md#metrics-port), and [`--metrics-push-port`](../Reference/Pantheon-CLI-Syntax.md#metrics-push-port) 
options).

To run Pantheon exposing local ports for access: 
```bash
$ docker run -p <localportJSON-RPC>:8545 -p <localportWS>:8546 -p <localportP2P>:30303 pegasyseng/pantheon:latest --rpc-http-enabled --rpc-ws-enabled
```

!!!example
    To enable RPC calls to http://127.0.0.1:8545 and P2P discovery on http://127.0.0.1:13001:
    ```bash
    docker run -p 8545:8545 -p 13001:30303 pegasyseng/pantheon:latest --rpc-http-enabled
    ```
