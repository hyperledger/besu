#!/usr/bin/env bash

rm -rf build

# WEB3J=web3j
# WEB3J=../../../sidechains-web3j/besucodegen/build/distributions/besucodegen-4.6.0-SNAPSHOT/bin/besucodegen
WEB3J=../../../../../../../../../../../sidechains-web3j/besucodegen/build/install/besucodegen/bin/besucodegen

# Testing lockability
solc lockability/SimpleIsLockable.sol --allow-paths . --bin --abi --optimize -o build
solc lockability/SimpleIsLockableCrosschain.sol --allow-paths . --bin --abi --optimize -o build
$WEB3J solidity generate -cc -a=build/SimpleIsLockableCrosschain.abi -b=build/SimpleIsLockableCrosschain.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated
$WEB3J solidity generate -a=build/SimpleIsLockable.abi -b=build/SimpleIsLockable.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.lockability.generated

# Testing viewtxcall
solc viewtxcall/FooCtrt.sol --allow-paths . --bin --abi --overwrite --optimize -o build
solc viewtxcall/FooInt.sol --allow-paths . --bin --abi --overwrite --optimize -o build
solc viewtxcall/BarCtrt.sol --allow-paths . --bin --abi --overwrite --optimize -o build
solc viewtxcall/BarInt.sol --allow-paths . --bin --abi --overwrite --optimize -o build
$WEB3J solidity generate -cc -a=build/FooCtrt.abi -b=build/FooCtrt.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.viewtxcall.generated
$WEB3J solidity generate -cc -a=build/BarCtrt.abi -b=build/BarCtrt.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.viewtxcall.generated
$WEB3J solidity generate -cc -a=build/FooInt.abi -b=build/FooInt.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.viewtxcall.generated
$WEB3J solidity generate -cc -a=build/BarInt.abi -b=build/BarInt.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.viewtxcall.generated

