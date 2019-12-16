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
import "../common/Crosschain.sol";
import "./Ctrt2Int.sol";
import "./Ctrt3Int.sol";

contract Ctrt2 is Crosschain, Ctrt2Int {
    uint256 ctrt3ChainId;
    Ctrt3Int ctrt3;
    uint256 flag;

    uint256 public myChainId;
    uint256 public fromChainId;
    uint32 public myTxType;
    uint32 public consTxType;
    uint256 public coordChainId;
    uint256 public origChainId;
    uint256 public txId;
    address public coordCtrtAddr;
    address public fromAddr;

    constructor() public {
        flag = 0;
        consTxType = crosschainGetInfoTransactionType();
    }

    function setCtrt3ChainId(uint256 _ctrt3ChainId) public {
        ctrt3ChainId = _ctrt3ChainId;
    }

    function setCtrt3(address _ctrt3Addr) public {
        ctrt3 = Ctrt3Int(_ctrt3Addr);
    }

    function callCtrt3() external {
        crosschainTransaction(ctrt3ChainId, address(ctrt3), abi.encodeWithSelector(ctrt3.txfn.selector));
        myChainId = crosschainGetInfoBlockchainId();
        myTxType = crosschainGetInfoTransactionType();
        coordChainId = crosschainGetInfoCoordinationBlockchainId();
        origChainId = crosschainGetInfoOriginatingBlockchainId();
        fromChainId = crosschainGetInfoFromBlockchainId();
        txId = crosschainGetInfoCrosschainTransactionId();
        coordCtrtAddr = crosschainGetInfoCoordinationContractAddress();
        fromAddr = crosschainGetInfoFromAddress();
    }

    function viewfn() external view returns (uint256) {
        return crosschainGetInfoTransactionType();
    }
}