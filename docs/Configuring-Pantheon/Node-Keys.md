description: Pantheon private and public key used to identify node
<!--- END of page meta data -->

# Node Keys

Each node has a node key pair consisting of a node private key and node public key. 

## Node Private Key

If a `key` file does not exist in the data directory and the [`--node-private-key-file`](../Reference/Pantheon-CLI-Syntax.md#node-private-key-file) 
option is not specified when Pantheon is started, a node private key is generated and written to the `key` file. 
If Pantheon is stopped and restarted without deleting the `key` file, the same private key is used when Pantheon is restarted.

If a `key` file exists in the data directory when Pantheon is started, the node is started with the private key in the `key` file. 

!!!info
    The private key is not encrypted. 

## Node Public Key

The node public key is displayed in the log after starting Pantheon. Use the [`public-key`](../Reference/Pantheon-CLI-Syntax.md#public-key)  subcommand to export the public key to a file. 

The node public key is also referred to as the node ID. The node ID forms part of the [enode URL](Bootnodes.md#private-networks)  for a node. 

## Specifying a Custom Node Private Key File

Use the [`--node-private-key-file`](../Reference/Pantheon-CLI-Syntax.md#node-private-key-file) option to specify a custom `key` file in any location. 

If the `key` file exists, the node is started with the private key in the custom `key` file. If the custom `key` file does not exist, 
a node private key is generated and written to the custom `key` file.

For example, the following command either reads the node private key from the `privatekeyfile` or writes the generated private key to the `privatekeyfile`:

!!! example
    ```bash
    pantheon --node-private-key-file="/Users/username/privatekeyfile"
    ```