See `ethereum/p2p/src/test/resources/keys/README.md` which describes the details on how the 
certificates are created. The same CA are used to create miner1-miner6. 

For `PkiQbftAcceptanceTest`: 
`miner1`-`miner5` are signed with `partner1_ca` and `miner6` is signed with `partner2_ca`.
`miner5` and `miner6` are revoked and added in the crl list.
