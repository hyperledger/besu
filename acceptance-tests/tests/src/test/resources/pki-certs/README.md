See `ethereum/p2p/src/test/resources/keys/tls_context_factory/README.md` which describes the details on how the 
certificates are created. The same CA are used here as `tls_context_factory`. 

For `Pk1QbftAcceptanceTest`: 
`miner1`-`miner5` are signed with `compa_ca` and `miner6` is signed with `compb_ca`.
`miner5` and `miner6` are revoked and added in the crl list.
