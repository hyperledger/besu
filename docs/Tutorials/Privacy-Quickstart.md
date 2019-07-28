description: Pantheon private network with privacy enabled quickstart tutorial
<!--- END of page meta data -->

# Private Network with Privacy Enabled Quickstart Tutorial

The Private Network with Privacy Enabled Quickstart runs a private network of Pantheon and Orion nodes managed by Docker Compose.
It is an expanded version of the [Private Network Quickstart](Private-Network-Quickstart.md). 

You can use the [Block Explorer](../Tutorials/Private-Network-Quickstart.md#block-explorer), 
make [JSON-RPC requests](../Tutorials/Private-Network-Quickstart.md#run-json-rpc-requests), and 
create [transactions using Metamask](../Tutorials/Private-Network-Quickstart.md#creating-a-transaction-using-metamask)
as described in the [Private Network Quickstart tutorial](Private-Network-Quickstart.md). 
This tutorial describes how to use the examples provided in the EEAJS library to [create and send private transactions](#send-private-transactions-and-read-values). 

!!! important 
    The quickstart runs a private network suitable for education or demonstration purposes. 
    The quickstart is not intended for running production networks. 

## Prerequisites

To run this tutorial, you must have the following installed:

- MacOS or Linux 
    
    !!! important 
        The Private Network Quickstart is not supported on Windows. If using Windows, run the quickstart
        inside a Linux VM such as Ubuntu. 

- [Docker and Docker-compose](https://docs.docker.com/compose/install/) 

- [Nodejs](https://nodejs.org/en/download/)

- [Git command line](https://git-scm.com/)

- [Curl command line](https://curl.haxx.se/download.html) 

## Clone Pantheon Quickstart Source Code

Clone the repository from the `pantheon-quickstart` repository where `<version>` is replaced with the latest version (`{{ versions.quickstart }}`). 

```bash tab="Command"
git clone --branch <version> https://github.com/PegaSysEng/pantheon-quickstart.git
```

```bash tab="Example"
git clone --branch {{ versions.quickstart }} https://github.com/PegaSysEng/pantheon-quickstart.git
```

## Clone EEAJS Libraries 

Clone the `PegaSysEng/eeajs` library: 

```bash
git clone https://github.com/PegaSysEng/eeajs.git
```

In the `eeajs` directory: 
   
```bash
npm install
```

## Start the Private Network with Privacy Enabled 

In the `pantheon-quickstart/privacy` directory, start the network: 

```bash
./run.sh
```

The Docker images are pulled and network started.  Pulling the images takes a few minutes the first time.
The network details are displayed. 

```bash
       Name                      Command               State                              Ports                           
--------------------------------------------------------------------------------------------------------------------------
privacy_bootnode_1    /opt/pantheon/bootnode_sta ...   Up      30303/tcp, 8545/tcp, 8546/tcp                              
privacy_explorer_1    nginx -g daemon off;             Up      0.0.0.0:32771->80/tcp                                      
privacy_minernode_1   /opt/pantheon/node_start.s ...   Up      30303/tcp, 8545/tcp, 8546/tcp                              
privacy_node1_1       /opt/pantheon/node_start.s ...   Up      30303/tcp, 0.0.0.0:20000->8545/tcp, 0.0.0.0:20001->8546/tcp
privacy_node2_1       /opt/pantheon/node_start.s ...   Up      30303/tcp, 0.0.0.0:20002->8545/tcp, 0.0.0.0:20003->8546/tcp
privacy_node3_1       /opt/pantheon/node_start.s ...   Up      30303/tcp, 0.0.0.0:20004->8545/tcp, 0.0.0.0:20005->8546/tcp
privacy_orion1_1      /orion/bin/orion data/data ...   Up                                                                 
privacy_orion2_1      /orion/bin/orion data/data ...   Up                                                                 
privacy_orion3_1      /orion/bin/orion data/data ...   Up                                                                 
privacy_rpcnode_1     /opt/pantheon/node_start.s ...   Up      30303/tcp, 8545/tcp, 8546/tcp                              
****************************************************************
JSON-RPC HTTP service endpoint      : http://localhost:32771/jsonrpc   *
JSON-RPC WebSocket service endpoint : ws://localhost:32771/jsonws   *
Web block explorer address          : http://localhost:32771   *                                                                             
****************************************************************
```

## Send Private Transactions and Read Values 

The Event Emitter script deploys a contract with a privacy group of Node1 and Node2. That is, the other nodes
cannot access the contract. After deploying the contract, Event Emitter stores a value. 

In the `eeajs` directory, run `eventEmitter.js`: 

```bash
node example/eventEmitter.js
```

!!! tip 
    The network takes a minute or so to get started.  If you get a ` Error: socket hang up` error, the network
    isn't fully setup. Wait and then run the command again. 

The Event Emitter logs are displayed. 

```bash
Transaction Hash  0xe0776de9a9d4e30be0025c1308eed8bc45502cba9fe22c504a56e2fd95343e6f
Waiting for transaction to be mined ...
Private Transaction Receipt
 { contractAddress: '0x2f351161a80d74047316899342eedc606b13f9f8',
  from: '0xfe3b557e8fb62b89f4916b721be55ceb828dbd73',
  to: null,
  output:
   '0x6080604052600436106100565763ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416633fa4f245811461005b5780636057361d1461008257806367e404ce146100ae575b600080fd5b34801561006757600080fd5b506100706100ec565b60408051918252519081900360200190f35b34801561008e57600080fd5b506100ac600480360360208110156100a557600080fd5b50356100f2565b005b3480156100ba57600080fd5b506100c3610151565b6040805173ffffffffffffffffffffffffffffffffffffffff9092168252519081900360200190f35b60025490565b604080513381526020810183905281517fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f5929181900390910190a16002556001805473ffffffffffffffffffffffffffffffffffffffff191633179055565b60015473ffffffffffffffffffffffffffffffffffffffff169056fea165627a7a72305820c7f729cb24e05c221f5aa913700793994656f233fe2ce3b9fd9a505ea17e8d8a0029',
  logs: [] }
Waiting for transaction to be mined ...
Transaction Hash: 0xbf14d332fa4c8f50d90cb02d47e0f825b8b2ef987c975306f76a598f181f4698
Event Emited: 0x000000000000000000000000fe3b557e8fb62b89f4916b721be55ceb828dbd7300000000000000000000000000000000000000000000000000000000000003e8
Waiting for transaction to be mined ...
Get Value: 0x00000000000000000000000000000000000000000000000000000000000003e8
Waiting for transaction to be mined ...
Transaction Hash: 0x5b538c5690e3ead6e6f811ad23c853bc63b3bca91635b3b611e51d2797b5f073
Event Emited: 0x000000000000000000000000fe3b557e8fb62b89f4916b721be55ceb828dbd73000000000000000000000000000000000000000000000000000000000000002a
Waiting for transaction to be mined ...
Get Value: 0x000000000000000000000000000000000000000000000000000000000000002a
```

Call [`eth_getTransactionReceipt`](../Reference/Pantheon-API-Methods.md#eth_gettransactionreceipt) where:
 
* `<TransactionHash>` is the transaction hash displayed in the Event Emitter logs. 
* `<JSON-RPC Endpoint>` is the JSON-RPC HTTP service endpoint displayed when starting the network. 

```bash tab="curl HTTP request"
curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getTransactionReceipt","params":["<TransactionHash>"],"id":1}' <JSON-RPC Endpoint>
```
    
```bash tab="Example"
curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getTransactionReceipt","params":["0xe0776de9a9d4e30be0025c1308eed8bc45502cba9fe22c504a56e2fd95343e6f"],"id":1}' http://localhost:32771/jsonrpc
```

The transaction receipt for the [privacy marker transaction](../Privacy/Explanation/Private-Transaction-Processing.md) is displayed with a `contractAddress` of `null`. 

```json
{
  "jsonrpc" : "2.0",
  "id" : 1,
  "result" : {
    "blockHash" : "0xfacdc805f274553fcb2a12d3ef524f465c25e58626c27101c3e6f677297cdae9",
    "blockNumber" : "0xa",
    "contractAddress" : null,
    "cumulativeGasUsed" : "0x5db8",
    "from" : "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
    "gasUsed" : "0x5db8",
    "logs" : [ ],
    "logsBloom" : "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
    "status" : "0x1",
    "to" : "0x000000000000000000000000000000000000007e",
    "transactionHash" : "0xe0776de9a9d4e30be0025c1308eed8bc45502cba9fe22c504a56e2fd95343e6f",
    "transactionIndex" : "0x0"
  }
}
```

## Stop Network

Do one of the following to stop the network: 

* Stop the network: 

    ```bash
    ./stop.sh
    ```
    
* Stop the network and remove the containers and volumes: 

    ```bash
    ./remove.sh
    ```

* Stop the network and delete the Docker images: 

    ```bash
    ./delete.sh
    ```
