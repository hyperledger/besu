/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

pragma solidity >=0.4.0 <0.6.0;

import "./FooArgsInt.sol";
import "../common/Crosschain.sol";
import "./BarArgsInt.sol";

contract BarArgsCtrt is Crosschain, BarArgsInt {
    uint256 public fooChainId;
    FooArgsInt public fooCtrt;
    uint256[] arr;

    uint256 public flag;

    constructor() public {
        flag = 0;
    }

    function setProperties(uint256 _fooChainId, address _fooCtrtAaddr) public {
        fooChainId = _fooChainId;
        fooCtrt = FooArgsInt(_fooCtrtAaddr);
    }

    function bar(bytes32 a, string calldata str, bool cond) external {
        if(cond) {
            arr.push(3);
        } else {
            arr.push(6);
        }
        flag = crosschainViewUint256(fooChainId, address(fooCtrt), abi.encodeWithSelector(fooCtrt.foo.selector, arr, a, str));
    }

    function barUpdateState() external {
        uint256[3] memory magicNumArr = [uint256(256), 257, 258];
        string memory str = "magic";
        crosschainTransaction(fooChainId, address(fooCtrt), abi.encodeWithSelector(fooCtrt.updateState.selector, magicNumArr, str));
    }
}