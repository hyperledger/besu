description: Pantheon consensus protocols
<!--- END of page meta data -->

# Consensus Protocols 

Pantheon implements a number of consensus protocols: 

* Ethash (Proof of Work)
  
* [Clique](Clique.md) (Proof of Authority)
  
* [IBFT 2.0](IBFT.md) (Proof of Authority) 

* [Quorum IBFT 1.0](QuorumIBFT.md) (Proof of Authority) 

The genesis file specifies the consensus protocol for a chain in the `config` property: 

```json tab="Ethash"
{
   "config": {
     ...
     "ethash": {
    
   } 
  },
  ...  
}
```
    
```json tab="Clique"
{
  "config": {
    ....
    "clique": {
      ... 
   }
  },
  ...
}
```
    
```json tab="IBFT 2.0" 
{
  "config": {
    ....
    "ibft2": {
      ...     
   }
  },
  ...
}
``` 

```json tab="IBFT 1.0" 
{
  "config": {
    ....
    "ibft": {
      ...     
   }
  },
  ...
}
```

