pragma solidity ^0.5.12;

pragma solidity ^0.5.9;
import "./OnChainPrivacyGroupManagementInterface.sol";

contract OnChainPrivacyGroupManagementProxy is OnChainPrivacyGroupManagementInterface {

    address public implementation;

    constructor(address _implementation) public {
        implementation = _implementation;
    }

    function _setImplementation(address _newImp) internal {
        implementation = _newImp;
    }

    function addParticipants(bytes32[] memory _publicEnclaveKeys) public returns (bool) {
        OnChainPrivacyGroupManagementInterface privacyInterface = OnChainPrivacyGroupManagementInterface(implementation);
        return privacyInterface.addParticipants(_publicEnclaveKeys);
    }

    function getParticipants() view public returns (bytes32[] memory) {
        OnChainPrivacyGroupManagementInterface privacyInterface = OnChainPrivacyGroupManagementInterface(implementation);
        return privacyInterface.getParticipants();
    }

    function removeParticipant(bytes32 _member) public returns (bool) {
        OnChainPrivacyGroupManagementInterface privacyInterface = OnChainPrivacyGroupManagementInterface(implementation);
        bool result = privacyInterface.removeParticipant(_member);
        if (result) {
            emit ParticipantRemoved(_member);
        }
    return result;
    }

    function lock() public {
        OnChainPrivacyGroupManagementInterface privacyInterface = OnChainPrivacyGroupManagementInterface(implementation);
        return privacyInterface.lock();
    }

    function unlock() public {
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

    function canUpgrade() external returns (bool) {
        OnChainPrivacyGroupManagementInterface privacyInterface = OnChainPrivacyGroupManagementInterface(implementation);
        return privacyInterface.canUpgrade();
    }

    function upgradeTo(address _newImplementation) external {
        require(this.canExecute(), "The contract is locked.");
        require(implementation != _newImplementation, "The contract to upgrade to has to be different from the current management contract.");
        require(this.canUpgrade(), "Not allowed to upgrade the management contract.");
        bytes32[] memory participants = this.getParticipants();
        _setImplementation(_newImplementation);
        OnChainPrivacyGroupManagementInterface privacyInterface = OnChainPrivacyGroupManagementInterface(implementation);
        privacyInterface.addParticipants(participants);
    }

    event ParticipantRemoved(
        bytes32 publicEnclaveKey
    );


}
