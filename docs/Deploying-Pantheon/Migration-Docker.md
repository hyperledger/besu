description: Migrating from pre v1.2 Docker image to v1.2 Docker image  
<!--- END of page meta data -->

# Migrating from pre-1.2 Docker Image to 1.2+

## Before v1.2

The Pantheon Docker image had an entry-script that automatically added a number of options 
to the Pantheon command line. The options could not be set using command line arguments.  

The options automatically added to the Pantheon command line for the Pantheon Docker image before v1.2 were: 

* If the file existed: 
    
    - [`--config-file /etc/pantheon/pantheon.conf`](../Reference/Pantheon-CLI-Syntax.md#config-file)
    - [`--genesis-file /etc/pantheon/genesis.json`](../Reference/Pantheon-CLI-Syntax.md#genesis-file)
    - [`--rpc-http-authentication-credentials-file /etc/pantheon/rpc_http_auth_config.toml`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-authentication-credentials-file) 
    - [`--rpc-ws-authentication-credentials-file /etc/pantheon/rpc_ws_auth_config.toml`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-authentication-credentials-file)
    - [`--privacy-public-key-file /etc/pantheon/privacy_public_key`](../Reference/Pantheon-CLI-Syntax.md#privacy-public-key-file)
    - [`--permissions-nodes-config-file /etc/pantheon/permissions_config.toml`](../Reference/Pantheon-CLI-Syntax.md#permissions-nodes-config-file)
    - [`--permissions-accounts-config-file /etc/pantheon/permissions_config.toml`](../Reference/Pantheon-CLI-Syntax.md#permissions-accounts-config-file)

* [`--data-path /var/lib/pantheon`](../Reference/Pantheon-CLI-Syntax.md#data-path) 
* [`--rpc-http-host=0.0.0.0`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-host)
* [`--rpc-http-port=8545`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port)
* [`--rpc-ws-host=0.0.0.0`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-host)
* [`--rpc-ws-port=8546`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-port)
* [`--p2p-host=0.0.0.0`](../Reference/Pantheon-CLI-Syntax.md#p2p-host)
* [`--p2p-port=30303`](../Reference/Pantheon-CLI-Syntax.md#p2p-port)

The [`--node-private-key-file`](../Reference/Pantheon-CLI-Syntax.md#node-private-key-file) command line option
was not available and the node key was always read from the data path. 

## From v1.2 

All file options (for example, [`--config-file`](../Reference/Pantheon-CLI-Syntax.md#config-file)) no longer 
have a default. Add the relevant command line options to your Pantheon command line and specify the file path. 

The [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) default is now `/opt/pantheon`. 

The [`--node-private-key-file`](../Reference/Pantheon-CLI-Syntax.md#node-private-key-file) default is 
now `/opt/pantheon/key`. 

!!! important 
    Do not mount a volume at the default data path (`/opt/pantheon`). Mounting a volume at the default 
    data path path interferes with the operation of Pantheon and prevents Pantheon from safely launching. 
    
    To run a node that maintains the node state (key and database), [`--data-path` must be set to a location
    other than `/opt/pantheon` and a storage volume mounted at that location](../Getting-Started/Run-Docker-Image.md#starting-pantheon). 

The host and port options continue to default to the previously set values. 

!!! tip
    All command line options can be set using [environment variables](../Reference/Pantheon-CLI-Syntax.md#pantheon-environment-variables). 


