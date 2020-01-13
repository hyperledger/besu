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
 */

pragma solidity >=0.4.0 <0.6.0;

import "./FooArgsInt.sol";
import "./BarArgsInt.sol";
import "../common/Crosschain.sol";

contract FooArgsCtrt is Crosschain, FooArgsInt {
    uint256 public fooFlag;
    uint256 barChainId;
    BarArgsInt barCtrt;

    constructor() public {
        fooFlag = 0;
    }

    function setPropertiesForBar(uint256 _barChainId, address _barCtrtAddress) public {
        barChainId = _barChainId;
        barCtrt = BarArgsInt(_barCtrtAddress);
    }

    function foo(uint256[] calldata arg1, bytes32 a, string calldata str) external view returns (uint256) {
        return arg1[arg1.length-1] + uint256(a) + bytes(str).length;
    }

    function updateState(uint256[] calldata magicNumArr, string calldata str) external {
        fooFlag = magicNumArr[0] + bytes(str).length;
    }
}