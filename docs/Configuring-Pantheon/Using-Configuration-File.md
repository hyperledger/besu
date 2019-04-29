# Using a Configuration File

To specify command line options in a file, use a TOML configuration file. 

The configuration file can be saved and reused across node startups. To specify the configuration file,
use the [`--config-file`](../Reference/Pantheon-CLI-Syntax.md#config-file) option. 

!!!note
    The [`--config-file`](../Reference/Pantheon-CLI-Syntax.md#config-file) option is not used when running Pantheon from the [Docker image](../Getting-Started/Run-Docker-Image.md). 
    Use a bind mount to [specify a configuration file with Docker](../Getting-Started/Run-Docker-Image.md#custom-configuration-file). 

To override an option specified in the configuration file, specify the same option on the command line. 
When an option is specified in both the configuration file and the command line, Pantheon is started with the command line value.  

## TOML Specification

The configuration file must be a valid TOML file and is composed of key/value pairs. Each key is the 
same as the corresponding command line option name without the leading dashes (`--`).

Values must be be specified according to TOML specifications for string, numbers, arrays, and booleans.
Specific differences between the command line and the TOML file format are: 

* Comma-separated lists on the command line are string arrays in the TOML file 
* File paths, hexadecimal numbers, URLs, and &lt;host:port> values must be enclosed in quotes. 

!!!tip
    The [command line reference](../Reference/Pantheon-CLI-Syntax.md) includes configuration file examples for each option.  

!!!example "Example TOML configuration file"
    ```toml
    # Valid TOML config file
    data-path="~/pantheondata" # Path
    
    # Network
    bootnodes=["enode://001@123:4567", "enode://002@123:4567", "enode://003@123:4567"]
    
    p2p-host="1.2.3.4"
    p2p-port=1234
    max-peers=42
    
    rpc-http-host="5.6.7.8"
    rpc-http-port=5678
    
    rpc-ws-host="9.10.11.12"
    rpc-ws-port=9101
    
    # Chain
    genesis-file="~/genesis.json" # Path to the custom genesis file
    
    # Mining
    miner-enabled=true
    miner-coinbase="0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"
    ```
    
!!!example "Starting Pantheon with a Configuration File"
    ```bash
    pantheon --config-file=/home/me/me_node/config.toml
    ```
