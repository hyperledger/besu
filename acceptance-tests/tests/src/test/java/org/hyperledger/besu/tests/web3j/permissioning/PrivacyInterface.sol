pragma solidity ^0.5.9;

interface PrivacyInterface {

    function addParticipants(bytes32 enclaveKey, bytes32[] calldata accounts) external returns (bool);

    function getParticipants(bytes32 enclaveKey) external view returns (bytes32[] memory);
}
