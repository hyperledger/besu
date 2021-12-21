/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
pragma solidity >=0.7.0 <0.9.0;

// compile with:
// solc RevertReason.sol --bin --abi --optimize --overwrite -o .
// then create web3j wrappers with:
// web3j solidity generate -b ./generated/RevertReason.bin -a ./generated/RevertReason.abi -o ../../../../../ -p org.hyperledger.besu.tests.web3j.generated
contract RevertReason {

    function revertWithRevertReason() public pure returns (bool) {
        revert("RevertReason");
    }

    function revertWithoutRevertReason() public pure returns (bool) {
        revert();
    }
}