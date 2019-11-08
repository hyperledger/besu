pragma solidity ^0.5.9;

contract PrivacyGroup {

    event MemberAdded(
        bool adminAdded,
        address account,
        string message
    );

    address[] public whitelist;
    mapping(address => uint256) private indexOf;

    modifier onlyMember() {
        require(isMember(msg.sender));
        _;
    }

    function isMember(address _account) internal view returns (bool) {
        return indexOf[_account] != 0;
    }

    function addMember(address _account) internal returns (bool) {
        if (indexOf[_account] == 0) {
            indexOf[_account] = whitelist.push(_account);
            return true;
        }
        return false;
    }

    function getParticipants() onlyMember public view returns (address[] memory) {
        return whitelist;
    }

    function addParticipants(address[] memory accounts) public onlyMember returns (bool) {
        bool allAdded = true;
        for (uint i = 0; i < accounts.length; i++) {
            if (msg.sender == accounts[i]) {
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


}