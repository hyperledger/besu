pragma solidity ^0.5.9;

import "./PrivacyInterface.sol";

contract UpgradedPrivacyGroup is PrivacyInterface {


    event MemberAdded(
        bool adminAdded,
        bytes32 account,
        string message
    );

    string private name;
    string private description;
    bytes32 private creator;
    bytes32[] private whitelist;
    mapping(bytes32 => uint256) private indexOf;
    address private proxyAddress;

    constructor(bytes32 enclaveKey, bytes32[] memory members, string memory _name, string memory _description) public {
        addMember(enclaveKey);
        creator = enclaveKey;
        addAll(enclaveKey, members);
        name = _name;
        description = _description;
    }

    modifier onlyProxy() {
        require(msg.sender == proxyAddress);
        _;
    }

    function isMember(bytes32 _account) internal view returns (bool) {
        return indexOf[_account] != 0;
    }

    function addMember(bytes32 _account) internal returns (bool) {
        if (indexOf[_account] == 0) {
            indexOf[_account] = whitelist.push(_account);
            return true;
        }
        return false;
    }

    function setProxy (bytes32 enclaveKey, address proxy) public {
        require(enclaveKey == creator);
        require(proxyAddress != proxy);
        proxyAddress = proxy;
    }

    function getParticipants(bytes32 enclaveKey) onlyProxy public view returns (bytes32[] memory) {
        require(isMember(enclaveKey));
        return whitelist;
    }

    function addParticipants(bytes32 enclaveKey, bytes32[] memory accounts) public onlyProxy returns (bool) {
        require(isMember(enclaveKey));
        return addAll(enclaveKey, accounts);
    }

    function addAll(bytes32 enclaveKey, bytes32[] memory accounts) internal returns (bool) {
        bool allAdded = true;
        for (uint i = 0; i < accounts.length; i++) {
            if (enclaveKey == accounts[i]) {
                emit MemberAdded(false, accounts[i], "Adding own account as a Member is not permitted");
                allAdded = allAdded && false;
            } else if (isMember(accounts[i])) {
                emit MemberAdded(false, accounts[i], "Account is already a Member");
                allAdded = allAdded && false;
            } else {
                bool result = addMember(accounts[i]);
                string memory message = result ? "Member account added successfully" : "Account is already a Member";
                emit MemberAdded(result, accounts[i], message);
                allAdded = allAdded && result;
            }
        }
        return allAdded;
    }

    function find(bytes32 value) internal view returns (uint) {
        uint i = 0;
        while (whitelist[i] != value) {
            i++;
        }
        return i;
    }

    function removeByIndex(uint i) internal {
        while (i < whitelist.length - 1) {
            whitelist[i] = whitelist[i + 1];
            i++;
        }
        whitelist.length--;
    }

    function removeByValue(bytes32 value) internal {
        uint i = find(value);
        removeByIndex(i);
    }

    function removeParticipant(bytes32 enclaveKey, bytes32 member) public returns (bool) {
        require(isMember(enclaveKey));
        if (isMember(member)) {
            removeByValue(member);
            indexOf[member] = 0;
            return true;
        }
        return false;
    }

}