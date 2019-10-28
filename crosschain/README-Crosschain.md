## General organization

We try to keep crosschain-related files in the crosschain directory, node configuration and data files in ~/crosschain_data, and everything should be runnable from the base directory of the repo.

## 1. Build Pantheon 

Skip tests (they take very long) and unpack the distribution file (which will leave the binary at  `build/install/pantheon/bin/pantheon`):
```
./gradlew installDist
```

### ... and prepare to run the scripts

They are written in JavaScript for coherence with Truffle and such.  
In macOS, it's currently recommended to use Node v10, not the latest v12. (Truffle suffers the same issue)
```bash
brew install node@10
``` 

Then install the libraries needed by the scripts:
```bash
npm install crosschain
```

## 2. Configuration and data location

### Quick start: 
To create 2 blockchains with chainIds 11 and 22 and with one node in each:
```bash
crosschain/create_chain.js 11 1 
crosschain/create_chain.js 22 1
```
 ----------------
### Explanation:
 
You will need to run 2 crosschain-enabled blockchains, each of them with at least 1 node.

To ease the related configuration, the script `crosschain/create_chain.js <chainId> <numNodes>` creates a set of configuration files (`genesis.json` and `config.toml`) for the specified number of nodes `numNodes`(currently) in one blockchain. You can specify a `chainId` <99. The data for each node will be stored in the directory ~/crosschain_data/chain***N***/node***M***.  For details on the process, see point 4.

You can run the script for as many blockchains as you need. Note that it will delete any previously existing configuration for the given `chainId`, so if you need to re-create a chain, just re-run the script.

Each node directory will contain the node's key and data directory.

Each node will listen for RPC at port `8000 + chainId*10 + nodeN`. For example, node 0 of chain 22 will listen at port 8220. The script will remind you of this when it finishes.

Each node will know how to reach the RPC port of the other instances through the `crosschain/resources/crosschain.config` file, which lists the correspondence chainId -> RPC address/port. This file is pre-configured with chains 11-99, in localhost:8000-8990.


## 3. Run Besu with the prepared config files

### Quick start: 
```bash
crosschain/run_node.js 11
```
... and in another console...
```bash
crosschain/run_node.js 22
```
--------------------
### Explanation:
The script `crosschain/run_node.js <chainId>` invokes Pantheon with the appropriate arguments to use each data directory. 



------------  

## 4. FYI: creating a Genesis File

As stated, this is all done for you by the script `crosschain/create_chain.js`, but as a FYI here is the manual process to create an IBFT2 crosschain-enabled genesis file with the account address of the validator nodes.  
The `genesis_template.json` file can be used as a template, but needs to be customized with the address of your node/s. To do so::
1. ensure that the node has its key someplace where it will not be deleted randomly - else it will be regenerated and you'll have to reconfigure the whole chain. 
2. obtain the node's account address
3. put the node account address into a valid JSON file
4. make Besu RLP-encode the extraData structure containing the address in the JSON file
5. Copy the extradata text into the "extradata" field of the genesis file



