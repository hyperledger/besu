pragma solidity ^0.5.12;

import "./OnChainPrivacyGroupManagementInterface.sol";

contract OnChainPrivacyGroupManagementProxy is OnChainPrivacyGroupManagementInterface {

    address public implementation;

    constructor(address _implementation) public {
        implementation = _implementation;
    }

    function _setImplementation(address _newImp) internal {
        implementation = _newImp;
    }

    function addParticipants(bytes32 enclaveKey, bytes32[] memory participants) public returns (bool) {
        OnChainPrivacyGroupManagementInterface privacyInterface = OnChainPrivacyGroupManagementInterface(implementation);
        return privacyInterface.addParticipants(enclaveKey, participants);
    }

    function getParticipants(bytes32 enclaveKey) view public returns (bytes32[] memory) {
        OnChainPrivacyGroupManagementInterface privacyInterface = OnChainPrivacyGroupManagementInterface(implementation);
        return privacyInterface.getParticipants(enclaveKey);
    }

    function removeParticipant(bytes32 enclaveKey, bytes32 account) public returns (bool) {
        OnChainPrivacyGroupManagementInterface privacyInterface = OnChainPrivacyGroupManagementInterface(implementation);
        return privacyInterface.removeParticipant(enclaveKey, account);
    }

    function lock(bytes32 enclaveKey) public {
        OnChainPrivacyGroupManagementInterface privacyInterface = OnChainPrivacyGroupManagementInterface(implementation);
        return privacyInterface.lock();
    }

    function unlock(bytes32 enclaveKey) public {
        OnChainPrivacyGroupManagementInterface privacyInterface = OnChainPrivacyGroupManagementInterface(implementation);
        return privacyInterface.unlock();
    }

    function canExecute() public view returns (bool) {
        OnChainPrivacyGroupManagementInterface privacyInterface = OnChainPrivacyGroupManagementInterface(implementation);
        return privacyInterface.canExecute();
    }

    function getVersion() public view returns (bytes32) {
        OnChainPrivacyGroupManagementInterface privacyInterface = OnChainPrivacyGroupManagementInterface(implementation);
        return privacyInterface.getVersion();
    }

    function canUpgrade(bytes32 _enclaveKey) external view returns (bool) {
        OnChainPrivacyGroupManagementInterface privacyInterface = OnChainPrivacyGroupManagementInterface(implementation);
        return privacyInterface.canUpgrade(_enclaveKey);
    }

    function upgradeTo(bytes32 _enclaveKey, address _newImplementation) external {
        require(implementation != _newImplementation);
        require(this.canUpgrade(_enclaveKey));
        bytes32[] memory participants = this.getParticipants(_enclaveKey);
        _setImplementation(_newImplementation);
        OnChainPrivacyGroupManagementInterface privacyInterface = OnChainPrivacyGroupManagementInterface(implementation);
        privacyInterface.addParticipants(_enclaveKey, participants);
    }
}
