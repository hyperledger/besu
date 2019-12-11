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
import "./NonLockableCtrtInt.sol";

contract BarCtrt is Crosschain, BarInt {
    uint256 public fooChainId;
    uint256 public nonLockableCtrtChainId;

    FooInt public fooCtrt;
    NonLockableCtrtInt public nonLockableCtrt;

    uint256 public flag;
    uint256 public vvflag;
    uint256 public vpflag;
    uint256 public ttvflag;
    uint256 public nonLockableViewFlag;

    constructor() public {
        flag = 0;
        vvflag = 0;
        vpflag = 0;
        ttvflag = 0;
        nonLockableViewFlag = 0;
    }

    function setProperties(uint256 _fooChainId, address _fooCtrtAaddr) public {
        fooChainId = _fooChainId;
        fooCtrt = FooInt(_fooCtrtAaddr);
    }

    function setPropertiesForNonLockableCtrt(uint256 _nonLockableCtrtChainId, address _nonLockableCtrtAddr) public {
        nonLockableCtrtChainId = _nonLockableCtrtChainId;
        nonLockableCtrt = NonLockableCtrtInt(_nonLockableCtrtAddr);
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
        return 1;
    }

    function barvv() external {
        vvflag = crosschainViewUint256(fooChainId, address(fooCtrt), abi.encodeWithSelector(fooCtrt.foovv.selector) );
    }

    function barvp() external {
        vpflag = crosschainViewUint256(fooChainId, address(fooCtrt), abi.encodeWithSelector(fooCtrt.foovp.selector) );
    }

    function bartv() external {
        crosschainTransaction(fooChainId, address(fooCtrt), abi.encodeWithSelector(fooCtrt.updateStateFromView.selector));
    }

    function bartp() external {
        crosschainTransaction(fooChainId, address(fooCtrt), abi.encodeWithSelector(fooCtrt.updateStateFromPure.selector));
    }

    function barttv() external {
        crosschainTransaction(fooChainId, address(fooCtrt), abi.encodeWithSelector(fooCtrt.updateStateFromTxView.selector));
    }

    function callNonLockableCtrtView() external {
        nonLockableViewFlag = crosschainViewUint256(nonLockableCtrtChainId, address(nonLockableCtrt), abi.encodeWithSelector(nonLockableCtrt.viewfn.selector));
    }

    function callNonLockableCtrtTx() external {
        crosschainTransaction(nonLockableCtrtChainId, address(nonLockableCtrt), abi.encodeWithSelector(nonLockableCtrt.updateState.selector));
    }
}