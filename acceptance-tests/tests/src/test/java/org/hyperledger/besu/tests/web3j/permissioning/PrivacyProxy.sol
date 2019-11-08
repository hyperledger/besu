pragma solidity ^0.5.9;

contract PrivacyProxy {
    address public owner;
    address public implementation;

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

    function() payable external {
        address impl = implementation;
        require(impl != address(0));
        assembly {
            let ptr := mload(0x40)
            calldatacopy(ptr, 0, calldatasize)
            let result := delegatecall(gas, impl, ptr, calldatasize, 0, 0)
            let size := returndatasize
            returndatacopy(ptr, 0, size)

            switch result
            case 0 {revert(ptr, size)}
            default {return (ptr, size)}
        }
    }

    function _setImplementation(address _newImp) internal {
        implementation = _newImp;
    }
}
