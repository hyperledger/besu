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
solc viewtxcall/BarCtrt.sol --allow-paths . --bin --abi --overwrite --optimize -o build
solc viewtxcall/Bar2Ctrt.sol --allow-paths . --bin --abi --overwrite --optimize -o build
solc viewtxcall/NonLockableCtrt.sol --allow-paths . --bin --abi --overwrite --optimize -o build
solc viewtxcall/LockableCtrt.sol --allow-paths . --bin --abi --overwrite --optimize -o build
$WEB3J solidity generate -cc -a=build/FooCtrt.abi -b=build/FooCtrt.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.viewtxcall.generated
$WEB3J solidity generate -cc -a=build/BarCtrt.abi -b=build/BarCtrt.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.viewtxcall.generated
$WEB3J solidity generate -cc -a=build/Bar2Ctrt.abi -b=build/Bar2Ctrt.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.viewtxcall.generated
$WEB3J solidity generate -a=build/NonLockableCtrt.abi -b=build/NonLockableCtrt.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.viewtxcall.generated
$WEB3J solidity generate -cc -a=build/LockableCtrt.abi -b=build/LockableCtrt.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.viewtxcall.generated

# Testing GetInfo precompiles
solc getinfo/Ctrt1.sol --allow-paths . --bin --abi --overwrite --optimize -o build
solc getinfo/Ctrt2.sol --allow-paths . --bin --abi --overwrite --optimize -o build
solc getinfo/Ctrt3.sol --allow-paths . --bin --abi --overwrite --optimize -o build
$WEB3J solidity generate -cc -a=build/Ctrt1.abi -b=build/Ctrt1.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.getinfo.generated
$WEB3J solidity generate -cc -a=build/Ctrt2.abi -b=build/Ctrt2.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.getinfo.generated
$WEB3J solidity generate -cc -a=build/Ctrt3.abi -b=build/Ctrt3.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.getinfo.generated
