pragma solidity ^0.5.9;

import "./PrivacyInterface.sol";

contract PrivacyProxy is PrivacyInterface{
    address private owner;
    address private implementation;

    constructor(address _implementation) public {
        owner = msg.sender;
        implementation = _implementation;
    }

    modifier onlyOwner() {
        require(msg.sender == owner);
        _;
    }

    function upgradeTo(address _newImplementation) external onlyOwner {
        require(implementation != _newImplementation);
        _setImplementation(_newImplementation);
    }

    function _setImplementation(address _newImp) internal {
        implementation = _newImp;
    }

    function addParticipants(bytes32 enclaveKey, bytes32[] memory accounts) public returns (bool) {
        PrivacyInterface privacyInterface = PrivacyInterface(implementation);
        return privacyInterface.addParticipants(enclaveKey, accounts);
    }

    function getParticipants(bytes32 enclaveKey) view public returns (bytes32[] memory) {
        PrivacyInterface privacyInterface = PrivacyInterface(implementation);
        return privacyInterface.getParticipants(enclaveKey);
    }

}
