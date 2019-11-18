pragma solidity ^0.5.9;

interface PrivacyInterface {

    function addParticipants(address[] calldata accounts) external returns (bool);

    function getParticipants() external view returns (address[] memory);
}
