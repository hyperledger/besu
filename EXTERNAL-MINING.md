# Besu External Mining
 
## Mining Software

EthMiner is recommended. You can [download EthMiner from Github](https://github.com/ethereum-mining/ethminer), available for Windows, Linux, and MacOS.

## External Mining with Besu

To mine with EthMiner, start Besu with HTTP RPC enabled, setting cors to the IP of the miners you want mining from Besu. 

If running in a development environment, the starting flags would be the following:

```
./bin/besu --network=dev --external-mining-enabled --rpc-http-enabled --rpc-http-cors-origins=all
```

This starts Besu in a developer network with JSON RPC available at localhost:8545, with cors set to all IPs.

Once initiated, you must wait until work is available before starting EthMiner. Once work is availabe, start EthMiner pointing to the JSON RPC endpoint. From the directory containing EthMiner:

```
<EthMiner> -P http://<Your Ethereum Address>@<Host>:<Port>
```

An example on Windows would look like the following:

```
ethminer.exe -P http://0xBBAac64b4E4499aa40DB238FaA8Ac00BAc50811B@127.0.0.1:8545
```

Other computers can mine to this Besu node if cors allows it, by setting the host to the IP of the computer/server running Besu, or the hostname of the server running Besu. 

You can test when work is available using CURL in a command line:

```
curl -X POST --data "{"jsonrpc":"2.0","method":"eth_getWork","params":[],"id":73}" localhost:8545
```

From Windows Command Prompt, qoutes are special characters.

```
curl -X POST --data "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getWork\",\"params\":[],\"id\":73}" localhost:8545
```

If work is available for mining, you should see a response similar to the following:

```
{"jsonrpc":"2.0","id":73,"result":["0xc9a815cfff9186b3b1ebb286ff71156b2605c4ca45e2413418c8054688011706","0x0000000000000000000000000000000000000000000000000000000000000000","0x028f5c28f5c28f5c28f5c28f5c28f5c28f5c28f5c28f5c28f5c28f5c28f5c28f"]}
```

If EthMiner disappears after building or downloading, check with your anti-virus software, allow EthMiner to run, and redownload. 