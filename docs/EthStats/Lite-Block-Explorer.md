description: Lite Block Explorer
<!--- END of page meta data -->

# EthStats Lite Block Explorer

Use the [EthStats Lite Block Explorer](https://lite.ethstats.io/) to explore blockchain data at the block, transaction, 
and account level.
 
The Lite Block Explorer is a client-side only web application that connects to an Ethereum 
JSON RPC node. No server, hosting, or trusting third parties to display the blockchain data is 
required. 

!!! note 
     The EthStats Lite Block Explorer is an [Alethio product](https://aleth.io/).

## Prerequisites

[Docker](https://docs.docker.com/install/) or [npm](https://www.npmjs.com/get-npm)

## Run Using Docker

To run the Lite Block Explorer using the Docker image: 

1. Start Pantheon with the [`--rpc-http-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-enabled) option. 

    !!! example 
        
        To run Pantheon in development mode:
        
        ```bash
        pantheon --network=dev --miner-enabled --miner-coinbase=0xfe3b557e8fb62b89f4916b721be55ceb828dbd73 --rpc-http-cors-origins="all" --host-whitelist="all" --rpc-http-enabled --data-path=/tmp/tmpDatdir
        ```

1. Run the `alethio/ethstats-lite-explorer` Docker image specifying the RPC HTTP URL (`http://localhost:8545` in this example): 

    ```bash
    docker run -p 80:80 -e NODE_URL=http://localhost:8545 alethio/ethstats-lite-explorer
    ```

1. Open [localhost](http://localhost) in your browser to view the Lite Block Explorer. 

## Install and Run 

1. Clone the `ethstats-lite-explorer` repository: 
   
    ```bash
    git clone https://github.com/Alethio/ethstats-lite-explorer.git
    ```

1. Change into the `ethstats-lite-explorer` directory: 
   ```bash
   cd ethstats-lite-explorer
   ```

1. Install npm packages: 

    ```bash
    npm install
    ```

1. Copy the sample environment variables: 

    ```bash 
    cp .env.example .env.local
    ```
  
1. Update the `.env.local` file: 

    * Set `VUE_APP_NODE_URL` to the RPC HTTP URL of your node (`http://localhost:8545` in this example)
   
    * Remove other environment variables. 
   
1. In another terminal, start Pantheon with the [`--rpc-http-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-enabled) option. 

    !!! example 
        
        To run Pantheon in development mode:
        
        ```bash
        pantheon --network=dev --miner-enabled --miner-coinbase=0xfe3b557e8fb62b89f4916b721be55ceb828dbd73 --rpc-http-cors-origins="all" --host-whitelist="all" --rpc-http-enabled --data-path=/tmp/tmpDatdir
        ```
        
1. In the `ethstats-lite-explorer` directory, run the Lite Block Explorer in development mode: 

    ```bash
    npm run serve
    ```  
   
1. Open [localhost:8080](http://localhost:8080) in your browser to view the Lite Block Explorer.
   
## Lite Block Explorer Documentation 

See the EthStats Lite Block Explorer [GitHub repository](https://github.com/Alethio/ethstats-lite-explorer) 
for more documentation, including details on deploying the Lite Block Explorer. 
