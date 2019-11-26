#!/usr/bin/env bash
rm -rf build
solc SimpleIsLockable.sol --bin --abi --optimize -o build

# WEB3J=web3j
# WEB3J=../../../sidechains-web3j/besucodegen/build/distributions/besucodegen-4.6.0-SNAPSHOT/bin/besucodegen
WEB3J=../../../../../../../../../../../../../sidechains-web3j/besucodegen/build/install/besucodegen/bin/besucodegen

$WEB3J solidity generate -cc -a=build/SimpleIsLockable.abi -b=build/SimpleIsLockable.bin -o=build -p=org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated.1
$WEB3J solidity generate -a=build/SimpleIsLockable.abi -b=build/SimpleIsLockable.bin -o=build -p=org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated.2

