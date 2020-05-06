pragma solidity ^0.5.9;
import "./OnChainPrivacyGroupManagementInterface.sol";

contract DefaultOnChainPrivacyGroupManagementContract is OnChainPrivacyGroupManagementInterface {

    bool private _canExecute;
    bytes32 private _version;
    bytes32[] private distributionList;
    mapping(bytes32 => uint256) private distributionIndexOf;

    function getVersion() external view returns (bytes32) {
        return _version;
    }

    // overrides
    function canExecute() external view returns (bool) {
        return _canExecute;
    }

    function lock() public {
        require(_canExecute);
        _canExecute = false;
    }

    function unlock() public {
        require(!_canExecute);
        _canExecute = true;
    }

    function addParticipants(bytes32 _enclaveKey, bytes32[] memory _accounts) public returns (bool) {
        require(!_canExecute);
        if(distributionList.length == 0) {
            addParticipant(_enclaveKey);
        }
        require(isMember(_enclaveKey));
        bool result = addAll(_enclaveKey, _accounts);
        _canExecute = true;
        updateVersion();
        return result;
    }

    function removeParticipant(bytes32 _enclaveKey, bytes32 _account) public returns (bool) {
        require(isMember(_enclaveKey));
        bool result = removeInternal(_account);
        updateVersion();
        return result;
    }

    function getParticipants(bytes32 _enclaveKey) public view returns (bytes32[] memory) {
        require(isMember(_enclaveKey));
        return distributionList;
    }


    //internal functions
    function addAll(bytes32 _enclaveKey, bytes32[] memory _accounts) internal returns (bool) {
        bool allAdded = true;
        for (uint i = 0; i < _accounts.length; i++) {
            if (_enclaveKey == _accounts[i]) {
                emit ParticipantAdded(false, _accounts[i], "Adding own account as a Member is not permitted");
                allAdded = allAdded && false;
            } else if (isMember(_accounts[i])) {
                emit ParticipantAdded(false, _accounts[i], "Account is already a Member");
                allAdded = allAdded && false;
            } else {
                bool result = addParticipant(_accounts[i]);
                string memory message = result ? "Member account added successfully" : "Account is already a Member";
                emit ParticipantAdded(result, _accounts[i], message);
                allAdded = allAdded && result;
            }
        }
        return allAdded;
    }

    function isMember(bytes32 _account) internal view returns (bool) {
        return distributionIndexOf[_account] != 0;
    }

    function addParticipant(bytes32 _participant) internal returns (bool) {
        if (distributionIndexOf[_participant] == 0) {
            distributionIndexOf[_participant] = distributionList.push(_participant);
            return true;
        }
        return false;
    }

    function removeInternal(bytes32 _participant) internal returns (bool) {
        uint256 index = distributionIndexOf[_participant];
        if (index > 0 && index <= distributionList.length) {
            //move last address into index being vacated (unless we are dealing with last index)
            if (index != distributionList.length) {
                bytes32 lastAccount = distributionList[distributionList.length - 1];
                distributionList[index - 1] = lastAccount;
                distributionIndexOf[lastAccount] = index;
            }
            distributionList.length -= 1;
            distributionIndexOf[_participant] = 0;
            return true;
        }
        return false;
    }

    function updateVersion() internal returns (int) {
        _version = keccak256(abi.encodePacked(blockhash(block.number-1), block.coinbase, distributionList));
    }

    event ParticipantAdded(
        bool adminAdded,
        bytes32 account,
        string message
    );
}