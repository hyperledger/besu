description: Pantheon consensus protocols
<!--- END of page meta data -->

# IBFT 1.0 

Pantheon nodes can participate (submit transactions and receive blocks) in a Quorum [IBFT 1.0](https://github.com/ethereum/EIPs/issues/650) 
network but cannot be a validator. 

To connect to a Quorum IBFT 1.0 network:

1. In the Quorum IBFT 1.0 genesis file, update the consensus protocol specified in the `config` property
from `istanbul` to `ibft`:

    ```json
        "config": {
         ...
         "ibft": {
         ...
        }
    ```

1. Use the [`--genesis-file`](../Reference/Pantheon-CLI-Syntax.md#genesis-file) option to specify the 
IBFT 1.0 genesis file.