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
import "./FooInt.sol";
import "./BarInt.sol";
import "../common/Crosschain.sol";

contract FooCtrt is Crosschain, FooInt {
    uint256 public fooFlag;
    uint256 barChainId;
    BarInt barCtrt;

    constructor() public {
        fooFlag = 0;
    }

    function setProperties(uint256 _barChainId, address _barCtrtAddress) public {
        barChainId = _barChainId;
        barCtrt = BarInt(_barCtrtAddress);
    }

    function foo() external view returns (uint256) {
        return 1;
    }

    function updateState() external {
        fooFlag = 1;
    }

    function pureFoo() external pure returns (uint256) {
        return 2;
    }

    function foovv() external view returns (uint256) {
        return crosschainViewUint256(barChainId, address(barCtrt), abi.encodeWithSelector(barCtrt.viewfn.selector));
    }

    function foovp() external view returns (uint256) {
        return crosschainViewUint256(barChainId, address(barCtrt), abi.encodeWithSelector(barCtrt.purefn.selector));
    }
}