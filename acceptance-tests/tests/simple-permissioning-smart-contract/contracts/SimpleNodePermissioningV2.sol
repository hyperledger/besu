pragma solidity >=0.4.0 <0.6.0;
// THIS CONTRACT IS FOR TESTING PURPOSES ONLY
// DO NOT USE THIS CONTRACT IN PRODUCTION APPLICATIONS

contract SimpleNodePermissioning {
    /*
        Node port is not being considered because ATs use random ports
    */
    struct Enode {
        string enodeId;
        string enodeIp;
    }
    mapping(bytes => Enode) private allowlist;

    // Port is being ignored
    function connectionAllowed(string memory enodeId, string memory ip, uint16) public view returns (bool) {
        return enodeAllowed(enodeId, ip);
    }

    function enodeAllowed(string memory enodeId, string memory sourceEnodeIp) private view returns (bool){
        bytes memory key = computeKey(enodeId, sourceEnodeIp);
        // if enode was found, keccak256 will be different
        return keccak256(bytes(allowlist[key].enodeIp)) != keccak256("");
    }

    function addEnode(string memory enodeId, string memory enodeIp, uint16) public {
        Enode memory newEnode = Enode(enodeId, enodeIp);
        bytes memory key = computeKey(enodeId, enodeIp);
        allowlist[key] = newEnode;
    }

    function removeEnode(string memory enodeId, string memory enodeIp, uint16) public {
        bytes memory key = computeKey(enodeId, enodeIp);
        Enode memory zeros = Enode("", "");
        // replace enode with "zeros"
        allowlist[key] = zeros;
    }

    function computeKey(string memory enodeId, string memory enodeIp) private pure returns (bytes memory) {
        return abi.encode(enodeId, enodeIp);
    }
}
