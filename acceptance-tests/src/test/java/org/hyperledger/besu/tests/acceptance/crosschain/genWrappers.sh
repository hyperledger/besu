#!/bin/bash

# Expects that this script is run from the containing directory

mkdir -p lockable/generated
solc lockable/DummyCtrt.sol --allow-paths . --bin --abi --optimize --overwrite -o lockable/generated/
WEB3J=../../../../../../../../../../../sidechains-web3j/besucodegen/build/distributions/besucodegen-4.6.0-SNAPSHOT/bin/besucodegen
$WEB3J solidity generate -cc -a=lockable/generated/DummyCtrt.abi -b=lockable/generated/DummyCtrt.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.lockable.generated

mkdir -p nonlockable/generated
solc nonlockable/DummyCtrt.sol --allow-paths . --bin --abi --optimize --overwrite -o nonlockable/generated/
WEB3J=../../../../../../../../../../../sidechains-web3j/besucodegen/build/distributions/besucodegen-4.6.0-SNAPSHOT/bin/besucodegen
$WEB3J solidity generate -a=nonlockable/generated/DummyCtrt.abi -b=nonlockable/generated/DummyCtrt.bin -o=../../../../../../ -p=org.hyperledger.besu.tests.acceptance.crosschain.nonlockable.generated