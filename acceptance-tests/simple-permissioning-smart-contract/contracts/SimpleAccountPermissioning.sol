pragma solidity >=0.4.0 <0.6.0;
// THIS CONTRACT IS FOR TESTING PURPOSES ONLY
// DO NOT USE THIS CONTRACT IN PRODUCTION APPLICATIONS

contract SimpleAccountPermissioning {
    mapping (address => bool) private whitelist;
    uint256 private size;

    function transactionAllowed(
        address sender,
        address target,
        uint256 value,
        uint256 gasPrice,
        uint256 gasLimit,
        bytes memory payload)
    public view returns (bool) {
        return whitelistContains(sender);
    }

    function addAccount(address account) public {
        if (!whitelist[account]) {
            whitelist[account] = true;
            size++;
        }
    }

    function removeAccount(address account) public {
        if (whitelist[account]) {
            whitelist[account] = false;
            size--;
        }
    }

    // For testing purposes, this will return true if the whitelist is empty
    function whitelistContains(address account) public view returns(bool) {
        if (size == 0) {
            return true;
        } else {
            return whitelist[account];
        }
    }

    function getSize() public view returns(uint256) {
        return size;
    }
}