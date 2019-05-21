description: Configuration items specified in the genesis file 
<!--- END of page meta data -->

# Configuration Items

Network configuration items are specified in the genesis file.  
 
| Item                | Description                                                                                                                              |
|---------------------|-:----------------------------------------------------------------------------------------------------------------------------------------|
| Chain ID            | [Chain ID for the network](NetworkID-And-ChainID.md)                                                                                      |
| Milestone blocks    | [Milestone blocks for the network](#milestone-blocks)                                                                                    |
| `ethash`            | Specifies network uses [Ethash](../Consensus-Protocols/Overview-Consensus.md) and contains [`fixeddifficulty`](#Fixed Difficulty)         |
| `clique`            | Specifies network uses [Clique](../Consensus-Protocols/Clique.md) and contains [Clique configuration items](../Consensus-Protocols/Clique.md#genesis-file)                              |
| `ibft2`             | Specifies network uses [IBFT 2.0](../Consensus-Protocols/IBFT.md) and contains [IBFT 2.0 configuration items](../Consensus-Protocols/IBFT.md#genesis-file)                            |
| `contractSizeLimit` | Maximum contract size in bytes. Specify in [free gas networks](FreeGas.md). Default is `24576` and the maximum size is `2147483647`.     |
| `evmStackSize`      | Maximum stack size. Specify to increase the maximum stack size in private networks with very complex smart contracts. Default is `1024`. |


## Milestone Blocks 

In public networks, the milestone blocks specify the blocks at which the network changed protocol. 

!!! example "Ethereum Mainnet Milestone Blocks"
    ```json 
    {
      "config": {
        ...
        "homesteadBlock": 1150000,
        "daoForkBlock": 1920000,
        "daoForkSupport": true,
        "eip150Block": 2463000,
        "eip150Hash": "0x2086799aeebeae135c246c65021c82b4e15a2c451340993aacfd2751886514f0",
        "eip155Block": 2675000,
        "eip158Block": 2675000,
        "byzantiumBlock": 4370000,
        "constantinopleBlock": 7280000,
        "constantinopleFixBlock": 7280000,
        ...
      },
    }
    ```

In private networks, the milestone block defines the protocol version for the network. 

!!! example "Private Network Milestone Block"
    ```json 
    {
      "config": {
        ...
        "constantinopleFixBlock": 0,
        ...
      },
    }
    ```

## Fixed Difficulty 

Use `fixeddifficulty` to specify a fixed difficulty in private networks using Ethash.  

!!! example  
    ```json
    {
      "config": {
          ...
          "ethash": {
            "fixeddifficulty": 1000
          },
           
       },
      ...
    }
    ```
    