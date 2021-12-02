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

contract FlexiblePrivacyGroupManagementProxy is FlexiblePrivacyGroupManagementInterface {

    address public implementation;

    constructor(address _implementation) public {
        implementation = _implementation;
    }

    function _setImplementation(address _newImp) internal {
        implementation = _newImp;
    }

    function addParticipants(bytes32[] memory _publicEnclaveKeys) public override returns (bool) {
        FlexiblePrivacyGroupManagementInterface privacyInterface = FlexiblePrivacyGroupManagementInterface(implementation);
        return privacyInterface.addParticipants(_publicEnclaveKeys);
    }

    function getParticipants() view public override returns (bytes32[] memory) {
        FlexiblePrivacyGroupManagementInterface privacyInterface = FlexiblePrivacyGroupManagementInterface(implementation);
        return privacyInterface.getParticipants();
    }

    function removeParticipant(bytes32 _participant) public override returns (bool) {
        FlexiblePrivacyGroupManagementInterface privacyInterface = FlexiblePrivacyGroupManagementInterface(implementation);
        bool result = privacyInterface.removeParticipant(_participant);
        if (result) {
            emit ParticipantRemoved(_participant);
        }
    return result;
    }

    function lock() public override {
        FlexiblePrivacyGroupManagementInterface privacyInterface = FlexiblePrivacyGroupManagementInterface(implementation);
        return privacyInterface.lock();
    }

    function unlock() public override {
        FlexiblePrivacyGroupManagementInterface privacyInterface = FlexiblePrivacyGroupManagementInterface(implementation);
        return privacyInterface.unlock();
    }

    function canExecute() public view override returns (bool) {
        FlexiblePrivacyGroupManagementInterface privacyInterface = FlexiblePrivacyGroupManagementInterface(implementation);
        return privacyInterface.canExecute();
    }

    function getVersion() public view override returns (bytes32) {
        FlexiblePrivacyGroupManagementInterface privacyInterface = FlexiblePrivacyGroupManagementInterface(implementation);
        return privacyInterface.getVersion();
    }

    function canUpgrade() external override returns (bool) {
        FlexiblePrivacyGroupManagementInterface privacyInterface = FlexiblePrivacyGroupManagementInterface(implementation);
        return privacyInterface.canUpgrade();
    }

    function upgradeTo(address _newImplementation) external {
        require(this.canExecute(), "The contract is locked.");
        require(implementation != _newImplementation, "The contract to upgrade to has to be different from the current management contract.");
        require(this.canUpgrade(), "Not allowed to upgrade the management contract.");
        bytes32[] memory participants = this.getParticipants();
        _setImplementation(_newImplementation);
        FlexiblePrivacyGroupManagementInterface privacyInterface = FlexiblePrivacyGroupManagementInterface(implementation);
        privacyInterface.addParticipants(participants);
    }

    event ParticipantRemoved(
        bytes32 publicEnclaveKey
    );


}
