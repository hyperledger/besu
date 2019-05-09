pragma solidity >=0.4.0 <0.6.0;
// THIS CONTRACT IS FOR TESTING PURPOSES ONLY
// DO NOT USE THIS CONTRACT IN PRODUCTION APPLICATIONS

contract SimpleAccountPermissioning {
    mapping (address => bool) private whitelist;

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
        whitelist[account] = true;
    }
    function removeAccount(address account) public {
        whitelist[account] = false;
    }
    function whitelistContains(address account) public view returns(bool) {
        return whitelist[account];
    }
}
