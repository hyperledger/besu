pragma solidity ^0.5.9;

interface OnChainPrivacyGroupManagementInterface {

    function addParticipants(bytes32 enclaveKey, bytes32[] calldata participants) external returns (bool);

    function removeParticipant(bytes32 enclaveKey, bytes32 account) external returns (bool);

    function getParticipants(bytes32 enclaveKey) external view returns (bytes32[] memory);

    function lock() external;

    function unlock() external;

    function canExecute() external view returns (bool);

    function getVersion() external view returns (bytes32);
}
