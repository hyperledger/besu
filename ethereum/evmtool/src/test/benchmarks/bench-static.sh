# call
../../../build/install/evmtool/bin/evm  \
  --code=5B600080808060045AFA50600056 \
  --sender=0xd1cf9d73a91de6630c2bb068ba5fddf9f0deac09 \
  --receiver=0x588108d3eab34e94484d7cda5a1d31804ca96fe7 \
  --genesis=evmtool-genesis.json \
  --gas 100000000 \
  --repeat=10

# pop
../../../build/install/evmtool/bin/evm  \
  --code=5B600080808060045A505050505050600056 \
  --sender=0xd1cf9d73a91de6630c2bb068ba5fddf9f0deac09 \
  --receiver=0x588108d3eab34e94484d7cda5a1d31804ca96fe7 \
  --genesis=evmtool-genesis.json \
  --gas 100000000 \
  --repeat=10
