description: Pantheon private network quickstart tutorial
<!--- END of page meta data -->

# Private Network Quickstart tutorial

!!! important "Upcoming Change in v0.9" 
    In v0.9, the Private Network Quickstart will be moved to a separate repository and the existing Private Network Quickstart will be 
    removed from the Pantheon repository. This tutorial will be updated and use the Private Network Quickstart in the separate 
    repository. 

This tutorial describes how to use Pantheon to run a private network of Pantheon nodes in a Docker container.

!!! note
    To run the Private Network Quickstart, you must install Pantheon by [cloning and building](../Installation/Build-From-Source.md).
    
    If you have installed Pantheon from the [packaged binaries](Intallation/Install-Binaries) or are running the [Docker image](Run-Docker-Image), you can proceed with [Starting Pantheon](Starting-Pantheon).

## Prerequisites

To run this tutorial, you must have the following installed:

- For MacOS and Linux, [Docker and Docker-compose](https://docs.docker.com/compose/install/) 

- For Windows, [Docker for Windows](https://docs.docker.com/docker-for-windows/install/)

!!!important
    Docker for Windows requires Windows 10 Pro, Enterpise, or Education. The Private Network Quickstart does not support Docker Toolbox.

- [Git command line](https://git-scm.com/)

- [Curl command line](https://curl.haxx.se/download.html) 

- A web browser that supports [Metamask](https://metamask.io/) (currently Chrome, Firefox, Opera, and Brave), and has the MetaMask plug-in installed. This tutorial uses screenshots from Brave.


## Clone Pantheon Source Code

As indicated in [the installation section](../Installation/Build-From-Source.md#clone-the-pantheon-repository), clone the repository.

## Build Docker Images and Start Services and Network
 
This tutorial uses [Docker Compose](https://docs.docker.com/compose/) to simplify assembling images and 
running in a private network. To run the containers, go to the `pantheon` directory and run the following commands:

Run the following commands :

```bash tab="Linux/macOS"
# Shell script are provided in the Quickstart directory

quickstart/runPantheonPrivateNetwork.sh
```

```bat tab="Windows"
// Run the docker-compose commands directly

// Run the services and ask for 4 regular nodes
quickstart\docker-compose up -d --scale node=4

// List the endpoints
quickstart\docker-compose port explorer 80
```

This script builds Pantheon, builds the images and runs the containers. It will also scale the regular node container to four containers to simulate a network with enough peers to synchronize.

When the process ends, it lists the running services:

!!! example "Docker-compose services list example"
    ```log
            Name                       Command               State                              Ports                           
    -----------------------------------------------------------------------------------------------------------------------------
    quickstart_bootnode_1    /opt/pantheon/bootnode_sta ...   Up      30303/tcp, 8545/tcp, 8546/tcp                              
    quickstart_explorer_1    nginx -g daemon off;             Up      0.0.0.0:32770->80/tcp                                      
    quickstart_minernode_1   /opt/pantheon/node_start.s ...   Up      30303/tcp, 8545/tcp, 8546/tcp                              
    quickstart_node_1        /opt/pantheon/node_start.s ...   Up      30303/tcp, 8545/tcp, 8546/tcp                              
    quickstart_node_2        /opt/pantheon/node_start.s ...   Up      30303/tcp, 8545/tcp, 8546/tcp                              
    quickstart_node_3        /opt/pantheon/node_start.s ...   Up      30303/tcp, 8545/tcp, 8546/tcp                              
    quickstart_node_4        /opt/pantheon/node_start.s ...   Up      30303/tcp, 8545/tcp, 8546/tcp                              
    quickstart_rpcnode_1     /opt/pantheon/node_start.s ...   Up      30303/tcp, 0.0.0.0:32769->8545/tcp, 0.0.0.0:32768->8546/tcp
    ```

This is followed by a list of the endpoints:

!!! example "Endpoint list example"
    ```log
    ****************************************************************
    JSON-RPC HTTP service endpoint      : http://localhost:32770/jsonrpc   *
    JSON-RPC WebSocket service endpoint : ws://localhost:32770/jsonws   *
    Web block explorer address          : http://localhost:32770   *                                                                             
    ****************************************************************
    ```

- Use the **JSON-RPC HTTP service endpoint** to access the RPC node service from your Dapp or from cryptocurrency wallets such as Metamask.
- Use the **JSON-RPC WebSocket service endpoint** to access the web socket node service from your Dapp. Use the form `ws://localhost:32770/jsonws`.
- Use the **Web block explorer address** to display the block explorer web application. View the block explorer by entering the URL in your web browser.

To display the list of endpoints again, run the following shell command:

```bash tab="Linux/macOS"
# Shell script are provided in the Quickstart directory

quickstart/listQuickstartServices.sh
```

```bat tab="Windows"
// Run the docker-compose commands directly

quickstart\docker-compose port explorer 80
```

## Block Explorer

This tutorial uses the [Alethio light block explorer](https://aleth.io/).

### Run the Block Explorer

Access the explorer by copying and pasting the `Web block explorer address` displayed when starting the private network to your browser.

The block explorer displays a summary of the private network:

![Block Explorer](ExplorerSummary.png)

Notice the explorer indicates 6 peers: the 4 regular nodes, the mining node and the bootnode.

Click on the block number to the right of **Best Block** to display the block details. 

![Block Details](ExplorerBlockDetails.png)

You can explore blocks by clicking on the blocks under **Bk** down the left-hand side. 

You can search for a specific block, transaction hash, or address by clicking the magnifying glass in the top left-hand corner. 

![Explorer Search](ExplorerSearch.png)

## Run JSON-RPC Requests 

Now we're ready to run requests.

You can run RPC requests on `rpcnode`, the node that is exposed to the host in order to listen for requests. This tutorial uses [cURL](https://curl.haxx.se/download.html) to make JSON-RPC requests.

!!!tip
    **On Windows:** We suggest using [Postman](https://www.getpostman.com/) or a similar client to make RPC requests.
    
    Using curl via Command Prompt or Windows PowerShell might not work.

This tutorial uses the placeholder `http://localhost:http-rpc-port`. When you run this tutorial, replace `http-rpc-port` with the JSON-RPC HTTP service endpoint provided when you list the endpoints. (For example, `http://localhost:32770/jsonrpc`.) The dynamic docker port mapping changes each time you run the network.

### Requesting the Node Version

Run the following command from the host shell :

```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"web3_clientVersion","params":[],"id":1}' http://localhost:http-rpc-port
```

The result should be as follows: 

```json
{
   "jsonrpc" : "2.0",
   "id" : 1,
   "result" : "pantheon/1.0.0"
}
```
Here we simply query the version of the Pantheon node, which confirms the node is running.

Now if this works, let's see some more interesting requests.

### Counting Peers

Peers are the number of other nodes connected to the RPC node.

Poll the peer count using `net_peerCount`:

```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"net_peerCount","params":[],"id":1}' http://localhost:http-rpc-port
```

The result should be the following response, indicating that there are 6 peers:

```json
{
  "jsonrpc" : "2.0",
  "id" : 1,
  "result" : "0x6"
}
```

### Requesting the Most Recent Mined Block Number  

This provides the count of blocks already mined.

To do so, call `eth_blockNumber` to retrieve the number of the most recent block:

```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' http://localhost:http-rpc-port
```

The result of this call should be:

```json
{
  "jsonrpc" : "2.0",
  "id" : 1,
  "result" : "0x8b8"
}
```

Here the hexadecimal value `0x8b8` translates to `2232` in decimal; that many blocks have already been mined.

### Checking the Miner Account Balance (Coinbase)

Then call `eth_getBalance` to retrieve the balance of the mining address defined in the miner node:

```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getBalance","params":["0xfe3b557e8fb62b89f4916b721be55ceb828dbd73","latest"],"id":1}' http://localhost:http-rpc-port
```

The result of this call should be something like :

```json
{
  "jsonrpc" : "2.0",
  "id" : 1,
  "result" : "0x79f905c6fd34e80000"
}
```

!!!info
    0x79f905c6fd34e80000 = 2250000000000000000000 Wei (2250 Ether)
    
    you can use a unit [converter](https://etherconverter.online/) to go from wei to ether.

Wait a few seconds until new blocks are mined and make this call again. The balance should increase, 
meaning that the miner address successfully received the mining reward.

_Also you can see this information in the block explorer. It does exactly the same thing as we 
did manually, connecting to the rpc node using http JSON-RPC, but displays information on a web page._

### Creating a Transaction Using MetaMask

Now we'll use [MetaMask](https://metamask.io/) to send transactions. 

To send transactions, you first need to create an account or use one of the 3 accounts below created during
the genesis of this test network. 

{!global/test_accounts.md!}

!!!note
    Pantheon does not provide an accounts management system, so if you want to create your own account, you will have to use a third party tool like Metamask.


After you sign in to MetaMask, connect to the private network RPC endpoint by:

1. In the MetaMask network list, select **Custom RPC**.
1. In the **New RPC URL** field, enter the `JSON-RPC HTTP service endpoint` displayed when you started the private network.

Save the configuration and return to the MetaMask main screen. Your current network is now set to the private network RPC node.

[Import one of the existing accounts above into metamask](https://metamask.zendesk.com/hc/en-us/articles/360015489331-Importing-an-Account-New-UI-)
using the corresponding private key. 

!!!note
    Here we don't really care about securing the keys as it's just a tutorial, but be sure
    to secure your accounts when you run into a real usecase. This will be discussed in a more advanced
    chapter.**

Once this is done, try to [create another account from scratch](https://metamask.zendesk.com/hc/en-us/articles/360015289452-Creating-Additional-MetaMask-Wallets-New-UI-)
to send some ether to.

!!!info
    Of course remember that here we are dealing with valueless ether as we are not on the main network but on a local private network.

In MetaMask, select the new account and copy the account address by clicking the **...** button and selecting **Copy Address to clipboard**.

In the block explorer, search for the new account by clicking on the magnifying glass and pasting the account address into the search box. The account is displayed with a zero balance. 

[Send some ether](https://metamask.zendesk.com/hc/en-us/articles/360015488991-Sending-Ether-New-UI-) 
from the first account (containing some ether) to the new one (that have a zero balance).

Click refresh on the browser page displaying the new account. The updated balance is displayed and reflects the transaction completed using MetaMask. 

### Truffle Pet Shop Tutorial

With a couple of modifications, we can use the private network in this tutorial as the blockchain for the [PetShop tutorial on Truffle website](https://truffleframework.com/tutorials/pet-shop).

#### Prerequisites

* [Node.js v6+ LTS and npm](https://nodejs.org/en/) (comes with Node)

#### Install Truffle and Unpack Truffle Box

Install Truffle :

```bash
npm install -g truffle
```

!!!note
    `npm` requires `sudo` on Linux.  

Create a `pet-shop-tutorial` directory and move into it:

```bash
mkdir pet-shop-tutorial
cd pet-shop-tutorial
```

Unpack Pet Shop [truffle box](https://truffleframework.com/boxes): 

`truffle unbox pet-shop`

Install the [wallet](https://www.npmjs.com/package/truffle-privatekey-provider):

```bash
npm install truffle-privatekey-provider
```
!!!note
    `npm` requires `sudo` on Linux.

#### Modify the Pet Shop Example

Modify the `truffle.js` file in the `pet-shop-tutorial` directory to add our wallet provider:

```javascript
const PrivateKeyProvider = require("truffle-privatekey-provider");
const privateKey = "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";
const privateKeyProvider = new PrivateKeyProvider(privateKey, "<YOUR HTTP RPC NODE ENDPOINT>");

module.exports = {
  // See <http://truffleframework.com/docs/advanced/configuration>
  // for more about customizing your Truffle configuration!
  networks: {
    development: {
      host: "127.0.0.1",
      port: 7545,
      network_id: "*" // Match any network id
    },
    quickstartWallet: {
      provider: privateKeyProvider,
      network_id: "*"
    },
  }
};
```

Replace `<YOUR HTTP RPC NODE ENDPOINT>` with your HTTP RPC node endpoint (for example, `http://localhost:32770/jsonrpc`).

The private key is the miner address which means it will have funds. 

Once this is done, you can continue with the [regular tutorial steps](https://truffleframework.com/tutorials/pet-shop#directory-structure) on the Truffle website until Step 3 in the [Migration section](https://truffleframework.com/tutorials/pet-shop#migration).

#### Use Pantheon Private Network Instead of [Ganache](https://truffleframework.com/ganache)

We are going to use our private network instead of Ganache, so skip steps 3, 4, and 5 in the [Migration section](https://truffleframework.com/tutorials/pet-shop#migration). 

In step 4, specify our private network: 

```bash
truffle migrate --network quickstartWallet
```

Output similar to the following is displayed (your addresses will differ):

```log
Using network 'quickstartWallet'.

Running migration: 1_initial_migration.js
  Deploying Migrations...
  ... 0xfc1dbc1eaa14fa283c2c4415364579da0d195b3f2f2fefd7e0edb600a6235bdb
  Migrations: 0x9a3dbca554e9f6b9257aaa24010da8377c57c17e
Saving successful migration to network...
  ... 0x77cc6e9966b886fb74268f118b3ff44cf973d32b616ed4f050b3eabf0a31a30e
Saving artifacts...
Running migration: 2_deploy_contracts.js
  Deploying Adoption...
  ... 0x5035fe3ea7dab1d81482acc1259450b8bf8fefecfbe1749212aca86dc765660a
  Adoption: 0x2e1f232a9439c3d459fceca0beef13acc8259dd8
Saving successful migration to network...
  ... 0xa7b5a36e0ebc9c25445ce29ff1339a19082d0dda516e5b72c06ee6b99a901ec0
Saving artifacts...
```

Search for the deployed contracts and transactions in the block explorer using the addresses displayed in your output.

Continue with the regular tutorial steps in the [Testing the smart contract section](https://truffleframework.com/tutorials/pet-shop#testing-the-smart-contract).

To run the tests in the [Running the tests section](https://truffleframework.com/tutorials/pet-shop#running-the-tests), specify our private network: 

```bash
truffle test --network quickstartWallet
```

Output similar to the following is displayed: 
```log
Using network 'quickstartWallet'.

Compiling ./contracts/Adoption.sol...
Compiling ./test/TestAdoption.sol...
Compiling truffle/Assert.sol...
Compiling truffle/DeployedAddresses.sol...


  TestAdoption
    ✓ testUserCanAdoptPet (2071ms)
    ✓ testGetAdopterAddressByPetId (6070ms)
    ✓ testGetAdopterAddressByPetIdInArray (6077ms)


  3 passing (37s)
```

Continue with the regular tutorial steps in the [Creating a user interface to interact with the smart contract section](https://truffleframework.com/tutorials/pet-shop#creating-a-user-interface-to-interact-with-the-smart-contract).

We have already connected our private network to MetaMask so you can skip the [Installing and configuring MetaMask section](https://truffleframework.com/tutorials/pet-shop#installing-and-configuring-metamask).

Continue with the regular tutorial steps from the [Installing and configuring lite-server section](https://truffleframework.com/tutorials/pet-shop#installing-and-configuring-lite-server) to the end of the tutorial.

When you adopt pets in the browser and approve the transaction in MetaMask, you will be able to see the transactions in the block explorer. 

## Shut Down the Network and Remove the Containers
 
To shut down the network and delete all containers:

```bash tab="Linux/macOS"
# Shell script are provided in the Quickstart directory

quickstart/removePantheonPrivateNetwork.sh
```

```bat tab="Windows"
// Run the docker-compose commands directly

quickstart\docker-compose down
```

!!!note
    On Windows, the quickstart creates a volume called `quickstart_public-keys` but it's not automatically removed. Remove this volume using `docker volume rm quickstart_public-keys`.

## Stop and restart the Private Network without Removing the Containers 

To shut down the network without deleting the containers:

```bash tab="Linux/macOS"
# Shell script are provided in the Quickstart directory

quickstart/stopPantheonPrivateNetwork.sh
```

```bat tab="Windows"
// Run the docker-compose commands directly

quickstart\docker-compose stop
```

(This command will also stop other running containers unrelated to quickstart.)

To restart the private network:

```bash tab="Linux/macOS"
# Shell script are provided in the Quickstart directory

quickstart/resumePantheonPrivateNetwork.sh
```

```bat tab="Windows"
// Run the docker-compose commands directly

quickstart\docker-compose start
```