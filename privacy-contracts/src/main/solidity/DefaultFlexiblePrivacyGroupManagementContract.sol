/*
 * Copyright ConsenSys AG.
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
pragma solidity ^0.6.0;
import "./FlexiblePrivacyGroupManagementInterface.sol";

contract DefaultFlexiblePrivacyGroupManagementContract is FlexiblePrivacyGroupManagementInterface {

    address private _owner;
    bool private _canExecute;
    bytes32 private _version;
    bytes32[] private distributionList;
    mapping(bytes32 => uint256) private distributionIndexOf;

    function getVersion() external view override returns (bytes32) {
        return _version;
    }

    function canExecute() external view override returns (bool) {
        return _canExecute;
    }

    function lock() public override {
        require(_canExecute);
        require(tx.origin == _owner, "Origin not the owner.");
        _canExecute = false;
    }

    function unlock() public override {
        require(!_canExecute);
        require(tx.origin == _owner, "Origin not the owner.");
        _canExecute = true;
    }

    function addParticipants(bytes32[] memory _publicEnclaveKeys) public override returns (bool) {
        require(!_canExecute);
        if (_owner == address(0x0)) {
        // The account creating this group is set to be the owner
            _owner = tx.origin;
        }
        require(tx.origin == _owner, "Origin not the owner.");
        bool result = addAll(_publicEnclaveKeys);
        _canExecute = true;
        updateVersion();
        return result;
    }

    function removeParticipant(bytes32 _participant) public override returns (bool) {
        require(_canExecute);
        require(tx.origin == _owner, "Origin not the owner.");
        bool result = removeInternal(_participant);
        updateVersion();
        return result;
    }

    function getParticipants() public view override returns (bytes32[] memory) {
        return distributionList;
    }

    function canUpgrade() external override returns (bool) {
        return tx.origin == _owner;
    }


    //internal functions
    function addAll(bytes32[] memory _publicEnclaveKeys) internal returns (bool) {
        bool allAdded = true;
        for (uint i = 0; i < _publicEnclaveKeys.length; i++) {
            if (isMember(_publicEnclaveKeys[i])) {
                emit ParticipantAdded(false, _publicEnclaveKeys[i], "Account is already a Member");
                allAdded = allAdded && false;
            } else {
                bool result = addParticipant(_publicEnclaveKeys[i]);
                string memory message = result ? "Member account added successfully" : "Account is already a Member";
                emit ParticipantAdded(result, _publicEnclaveKeys[i], message);
                allAdded = allAdded && result;
            }
        }
        return allAdded;
    }

    function isMember(bytes32 _publicEnclaveKey) internal view returns (bool) {
        return distributionIndexOf[_publicEnclaveKey] != 0;
    }

    function addParticipant(bytes32 _publicEnclaveKey) internal returns (bool) {
        if (distributionIndexOf[_publicEnclaveKey] == 0) {
            distributionList.push(_publicEnclaveKey);
            distributionIndexOf[_publicEnclaveKey] = distributionList.length;
            return true;
        }
        return false;
    }

    function removeInternal(bytes32 _participant) internal returns (bool) {
        uint256 index = distributionIndexOf[_participant];
        if (index > 0 && index <= distributionList.length) {
            //move last address into index being vacated (unless we are dealing with last index)
            if (index != distributionList.length) {
                bytes32 lastPublicKey = distributionList[distributionList.length - 1];
                distributionList[index - 1] = lastPublicKey;
                distributionIndexOf[lastPublicKey] = index;
            }
            distributionList.pop();
            distributionIndexOf[_participant] = 0;
            return true;
        }
        return false;
    }

    function updateVersion() internal returns (int) {
        _version = keccak256(abi.encodePacked(blockhash(block.number-1), block.coinbase, distributionList));
    }

    event ParticipantAdded(
        bool success,
        bytes32 publicEnclaveKey,
        string message
    );
}
