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
import "../common/Crosschain.sol";
import "./BarInt.sol";

contract BarCtrt is Crosschain, BarInt {
    uint256 fooChainId;
    FooInt public fooCtrt;
    uint256 public flag;

    constructor() public {
        flag = 0;
    }

    function setProperties(uint256 _fooChainId, address _fooCtrtAaddr) public {
        fooChainId = _fooChainId;
        fooCtrt = FooInt(_fooCtrtAaddr);
    }

    function bar() external {
        flag = crosschainViewUint256(fooChainId, address(fooCtrt), abi.encodeWithSelector(fooCtrt.foo.selector) );
    }

    function barUpdateState() external {
        crosschainTransaction(fooChainId, address(fooCtrt), abi.encodeWithSelector(fooCtrt.updateState.selector) );
    }

    function pureBar() external {
        flag = crosschainViewUint256(fooChainId, address(fooCtrt), abi.encodeWithSelector(fooCtrt.pureFoo.selector) );
    }

    function viewfn() external view returns (uint256) {
        return 1;
    }

    function purefn() external pure returns (uint256) {
        return 2;
    }

    function barvv() external {
        flag = crosschainViewUint256(fooChainId, address(fooCtrt), abi.encodeWithSelector(fooCtrt.foovv.selector) );
    }

    function barvp() external {
        flag = crosschainViewUint256(fooChainId, address(fooCtrt), abi.encodeWithSelector(fooCtrt.foovp.selector) );
    }
}