pragma solidity ^0.5.9;
import "./OnChainPrivacyGroupManagementInterface.sol";

contract DefaultOnChainPrivacyGroupManagementContract is OnChainPrivacyGroupManagementInterface {

    address private _owner;
    bool private _canExecute;
    bytes32 private _version;
    bytes32[] private distributionList;
    mapping(bytes32 => uint256) private distributionIndexOf;

    function getVersion() external view returns (bytes32) {
        return _version;
    }

    function canExecute() external view returns (bool) {
        return _canExecute;
    }

    function lock() public {
        require(_canExecute);
        require(tx.origin == _owner, "Origin not the owner.");
        _canExecute = false;
    }

    function unlock() public {
        require(!_canExecute);
        require(tx.origin == _owner, "Origin not the owner.");
        _canExecute = true;
    }

    function addParticipants(bytes32[] memory _publicEnclaveKeys) public returns (bool) {
        require(!_canExecute);
        if (_owner == address(0x0)) {
        // The account creating this group is set to be the owner
            _owner = tx.origin;
        }
        require(tx.origin == _owner, "Origin not the owner.");
        bool result = addAll(_publicEnclaveKeys);
        _canExecute = true;
        updateVersion();
        return result;
    }

    function removeParticipant(bytes32 _member) public returns (bool) {
        require(_canExecute);
        require(tx.origin == _owner, "Origin not the owner.");
        bool result = removeInternal(_member);
        updateVersion();
        return result;
    }

    function getParticipants() public view returns (bytes32[] memory) {
        return distributionList;
    }

    function canUpgrade() external returns (bool) {
        return tx.origin == _owner;
    }


    //internal functions
    function addAll(bytes32[] memory _publicEnclaveKeys) internal returns (bool) {
        bool allAdded = true;
        for (uint i = 0; i < _publicEnclaveKeys.length; i++) {
            if (isMember(_publicEnclaveKeys[i])) {
                emit ParticipantAdded(false, _publicEnclaveKeys[i], "Account is already a Member");
                allAdded = allAdded && false;
            } else {
                bool result = addParticipant(_publicEnclaveKeys[i]);
                string memory message = result ? "Member account added successfully" : "Account is already a Member";
                emit ParticipantAdded(result, _publicEnclaveKeys[i], message);
                allAdded = allAdded && result;
            }
        }
        return allAdded;
    }

    function isMember(bytes32 _publicEnclaveKey) internal view returns (bool) {
        return distributionIndexOf[_publicEnclaveKey] != 0;
    }

    function addParticipant(bytes32 _publicEnclaveKey) internal returns (bool) {
        if (distributionIndexOf[_publicEnclaveKey] == 0) {
            distributionIndexOf[_publicEnclaveKey] = distributionList.push(_publicEnclaveKey);
            return true;
        }
        return false;
    }

    function removeInternal(bytes32 _member) internal returns (bool) {
        uint256 index = distributionIndexOf[_member];
        if (index > 0 && index <= distributionList.length) {
            //move last address into index being vacated (unless we are dealing with last index)
            if (index != distributionList.length) {
                bytes32 lastPublicKey = distributionList[distributionList.length - 1];
                distributionList[index - 1] = lastPublicKey;
                distributionIndexOf[lastPublicKey] = index;
            }
            distributionList.length -= 1;
            distributionIndexOf[_member] = 0;
            return true;
        }
        return false;
    }

    function updateVersion() internal returns (int) {
        _version = keccak256(abi.encodePacked(blockhash(block.number-1), block.coinbase, distributionList));
    }

    event ParticipantAdded(
        bool success,
        bytes32 publicEnclaveKey,
        string message
    );
}