pragma solidity >=0.4.0 <0.6.0;
// THIS CONTRACT IS FOR TESTING PURPOSES ONLY
// DO NOT USE THIS CONTRACT IN PRODUCTION APPLICATIONS

contract SimpleNodePermissioning {
    /*
        Node port is not being considered because ATs use random ports
    */
    struct Enode {
        string enodeId;
        bytes16 enodeHost;
    }
    mapping(bytes => Enode) private allowlist;

    // Port is being ignored
    function connectionAllowed(string memory enodeId, bytes16 ip, uint16) public view returns (bool) {
        return enodeAllowed(enodeId, ip);
    }

    function enodeAllowed(string memory enodeId, bytes16 sourceEnodeIp) private view returns (bool){
        bytes memory key = computeKey(enodeId, sourceEnodeIp);
        // if enode was found, host (bytes) is greater than zero
        return allowlist[key].enodeHost > 0;
    }

    function addEnode(string memory enodeId, bytes16 enodeIp, uint16) public {
        Enode memory newEnode = Enode(enodeId, enodeIp);
        bytes memory key = computeKey(enodeId, enodeIp);
        allowlist[key] = newEnode;
    }

    function removeEnode(string memory enodeId, bytes16 enodeIp, uint16) public {
        bytes memory key = computeKey(enodeId, enodeIp);
        Enode memory zeros = Enode("", bytes16(0));
        // replace enode with "zeros"
        allowlist[key] = zeros;
    }

    function computeKey(string memory enodeId, bytes16 enodeIp) private pure returns (bytes memory) {
        return abi.encode(enodeId, enodeIp);
    }
}
