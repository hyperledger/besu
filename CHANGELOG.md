# Changelog

### Java 11 Required from v1.2

From v1.2, Pantheon requires Java 11.  Pantheon on Java 8 is no longer supported.

### Docker Image Migration 

In v1.2, we removed the entry-point script from our Docker image. Refer to the [migration guide](https://docs.pantheon.pegasys.tech/en/latest/Deploying-Pantheon/High-Availability/)
for information on options that were previously automatically added to the Pantheon command line. 

## 1.2.2 

### Additions and Improvements
- Support large numbers for the `--network-id` option [\#1891](https://github.com/PegaSysEng/pantheon/pull/1891)
- Added eea\_getTransactionCount Json Rpc [\#1861](https://github.com/PegaSysEng/pantheon/pull/1861) 
- PrivacyMarkerTransaction to be signed with a randomly generated key [\#1844](https://github.com/PegaSysEng/pantheon/pull/1844) 
- Implement eth\_getproof JSON RPC API [\#1824](https://github.com/PegaSysEng/pantheon/pull/1824) (thanks to [matkt](https://github.com/matkt))
- Documentation updates include: 
  - [Improved navigation](https://docs.pantheon.pegasys.tech/en/latest/)
  - [Added permissioning diagram](https://docs.pantheon.pegasys.tech/en/latest/Concepts/Permissioning/Permissioning-Overview/#onchain)
  - [Added Responsible Disclosure policy](https://docs.pantheon.pegasys.tech/en/latest/Reference/Responsible-Disclosure/)
  - [Added `blocks export` subcommand](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI/Pantheon-CLI-Subcommands/#export)

### Technical Improvements  
- Update the `pantheon blocks export` command usage [\#1887](https://github.com/PegaSysEng/pantheon/pull/1887) (thanks to [matkt](https://github.com/matkt))
- Stop Returning null for 'pending' RPC calls [\#1883](https://github.com/PegaSysEng/pantheon/pull/1883) 
- Blake validation errors are hard errors [\#1882](https://github.com/PegaSysEng/pantheon/pull/1882) 
- Add test cases for trace\_replayBlockTransactions [\#1881](https://github.com/PegaSysEng/pantheon/pull/1881) 
- Simplify json rpc spec test setup [\#1880](https://github.com/PegaSysEng/pantheon/pull/1880) 
- Tweak JSON import format [\#1878](https://github.com/PegaSysEng/pantheon/pull/1878) 
- Transactions listeners should use the subscriber pattern [\#1877](https://github.com/PegaSysEng/pantheon/pull/1877) 
- Maven spotless [\#1876](https://github.com/PegaSysEng/pantheon/pull/1876) 
- Don't cache for localbalance [\#1875](https://github.com/PegaSysEng/pantheon/pull/1875) 
- EIP-1108 - Reprice alt\_bn128  [\#1874](https://github.com/PegaSysEng/pantheon/pull/1874) 
- Create stub trace\_replayBlockTransactions json-rpc method  [\#1873](https://github.com/PegaSysEng/pantheon/pull/1873) 
- Improve trace log [\#1870](https://github.com/PegaSysEng/pantheon/pull/1870)
- Pruning Command Line Flags [\#1869](https://github.com/PegaSysEng/pantheon/pull/1869) 
- Re-enable istanbul [\#1865](https://github.com/PegaSysEng/pantheon/pull/1865) 
- Fix logic to disconnect from peers on fork [\#1863](https://github.com/PegaSysEng/pantheon/pull/1863) 
- Blake 2b tweaks [\#1862](https://github.com/PegaSysEng/pantheon/pull/1862) 
- Sweep state roots before child nodes [\#1854](https://github.com/PegaSysEng/pantheon/pull/1854) 
- Update export subcommand to export blocks in rlp format [\#1852](https://github.com/PegaSysEng/pantheon/pull/1852)
- Updating docker tests to make it easier to follow & ensure it listens on the right interface on docker [\#1851](https://github.com/PegaSysEng/pantheon/pull/1851) 
- Disable Istanbul block [\#1849](https://github.com/PegaSysEng/pantheon/pull/1849) 
- Add read-only blockchain factory method [\#1845](https://github.com/PegaSysEng/pantheon/pull/1845) 
- Removing the release plugin in favour of the new process with branches [\#1843](https://github.com/PegaSysEng/pantheon/pull/1843) 
- Update Görli bootnodes [\#1842](https://github.com/PegaSysEng/pantheon/pull/1842) 
- Upgrade graphql library to version 13.0 [\#1834](https://github.com/PegaSysEng/pantheon/pull/1834) 
- Database versioning and enable multi-column database [\#1830](https://github.com/PegaSysEng/pantheon/pull/1830) 
- Fixes invalid JsonGetter, comment [\#1811](https://github.com/PegaSysEng/pantheon/pull/1811) (thanks to [josh-richardson](https://github.com/josh-richardson))
- Add EthSigner acceptance test [\#1655](https://github.com/PegaSysEng/pantheon/pull/1655) (thanks to [iikirilov](https://github.com/iikirilov))
- Support plugin Richdata APIs via implementation [\#1581](https://github.com/PegaSysEng/pantheon/pull/1581) 

## 1.2.1

### Additions and Improvements

- Removed the release plugin in favour of the new process with branches
[#1841](https://github.com/PegaSysEng/pantheon/pull/1841)
[#1843](https://github.com/PegaSysEng/pantheon/pull/1843)
[#1848](https://github.com/PegaSysEng/pantheon/pull/1848)
[#1855](https://github.com/PegaSysEng/pantheon/pull/1855)
- Updated Görli bootnodes [#1842](https://github.com/PegaSysEng/pantheon/pull/1842)
- Removed unnecessary test dependency [#1839](https://github.com/PegaSysEng/pantheon/pull/1839)
- Added warning when comments are used in genesis file [#1838](https://github.com/PegaSysEng/pantheon/pull/1838)
- Added an experimental flag for disabling timers [#1837](https://github.com/PegaSysEng/pantheon/pull/1837)
- Fixed FlatFileTaskCollection tests [#1833](https://github.com/PegaSysEng/pantheon/pull/1833)
- Added chain json import utility [#1832](https://github.com/PegaSysEng/pantheon/pull/1832)
- Added tests to AllNodesVisitor trie traversal [#1831](https://github.com/PegaSysEng/pantheon/pull/1831)
- Updated privateFrom to be required [#1829](https://github.com/PegaSysEng/pantheon/pull/1829) (thanks to [iikirilov](https://github.com/iikirilov))
- Made explicit that streamed accounts may be missing their address [#1828](https://github.com/PegaSysEng/pantheon/pull/1828)
- Refactored normalizeKeys method [#1826](https://github.com/PegaSysEng/pantheon/pull/1826)
- Removed dead parameters [#1825](https://github.com/PegaSysEng/pantheon/pull/1825)
- Added a nicer name for Corretto [#1819](https://github.com/PegaSysEng/pantheon/pull/1819)
- Changed core JSON-RPC method to support ReTestEth
[#1815](https://github.com/PegaSysEng/pantheon/pull/1815)
[#1818](https://github.com/PegaSysEng/pantheon/pull/1818)
- Added rewind to block functionality [#1814](https://github.com/PegaSysEng/pantheon/pull/1814)
- Added support for NoReward and NoProof seal engines [#1813](https://github.com/PegaSysEng/pantheon/pull/1813)
- Added strict short hex strings for retesteth [#1812](https://github.com/PegaSysEng/pantheon/pull/1812)
- Cleaned up genesis parsing [#1809](https://github.com/PegaSysEng/pantheon/pull/1809)
- Updating Orion to v1.3.2 [#1805](https://github.com/PegaSysEng/pantheon/pull/1805)
- Updaated newHeads subscription to emit events only for canonical blocks [#1798](https://github.com/PegaSysEng/pantheon/pull/1798)
- Repricing for trie-size-dependent opcodes [#1795](https://github.com/PegaSysEng/pantheon/pull/1795)
- Revised Istanbul Versioning assignemnts [#1794](https://github.com/PegaSysEng/pantheon/pull/1794)
- Updated RevertReason to return BytesValue [#1793](https://github.com/PegaSysEng/pantheon/pull/1793)
- Updated way priv_getPrivacyPrecompileAddress source [#1786](https://github.com/PegaSysEng/pantheon/pull/1786) (thanks to [iikirilov](https://github.com/iikirilov))
- Updated Chain ID opcode to return 0 as default [#1785](https://github.com/PegaSysEng/pantheon/pull/1785)
- Allowed fixedDifficulty=1 [#1784](https://github.com/PegaSysEng/pantheon/pull/1784)
- Updated Docker image defaults host interfaces [#1782](https://github.com/PegaSysEng/pantheon/pull/1782)
- Added tracking of world state account key preimages [#1780](https://github.com/PegaSysEng/pantheon/pull/1780)
- Modified PrivGetPrivateTransaction to take public tx hash [#1778](https://github.com/PegaSysEng/pantheon/pull/1778) (thanks to [josh-richardson](https://github.com/josh-richardson))
- Removed enclave public key from parameter
[#1789](https://github.com/PegaSysEng/pantheon/pull/1789)
[#1777](https://github.com/PegaSysEng/pantheon/pull/1777) (thanks to [iikirilov](https://github.com/iikirilov))
- Added storage key preimage tracking [#1772](https://github.com/PegaSysEng/pantheon/pull/1772)
- Updated priv_getPrivacyPrecompileAddress method return [#1766](https://github.com/PegaSysEng/pantheon/pull/1766) (thanks to [iikirilov](https://github.com/iikirilov))
- Added tests for permissioning with static nodes behaviour [#1764](https://github.com/PegaSysEng/pantheon/pull/1764)
- Added integration test for contract creation with privacyGroupId [#1762](https://github.com/PegaSysEng/pantheon/pull/1762) (thanks to [josh-richardson](https://github.com/josh-richardson))
- Added report node local address as the coinbase in Clique and IBFT
[#1758](https://github.com/PegaSysEng/pantheon/pull/1758)
[#1760](https://github.com/PegaSysEng/pantheon/pull/1760)
- Fixed private tx signature validation [#1753](https://github.com/PegaSysEng/pantheon/pull/1753)
- Updated CI configuration
[#1751](https://github.com/PegaSysEng/pantheon/pull/1751)
[#1835](https://github.com/PegaSysEng/pantheon/pull/1835)
- Added CLI flag for setting WorldStateDownloader task cache size [#1749](https://github.com/PegaSysEng/pantheon/pull/1749) (thanks to [matkt](https://github.com/matkt))
- Updated vertx to 2.8.0 [#1748](https://github.com/PegaSysEng/pantheon/pull/1748)
- changed RevertReason to BytesValue [#1746](https://github.com/PegaSysEng/pantheon/pull/1746)
- Added static nodes acceptance test [#1745](https://github.com/PegaSysEng/pantheon/pull/1745)
- Added report 0 hashrate when the mining coordinator doesn't support mining
[#1744](https://github.com/PegaSysEng/pantheon/pull/1744)
[#1757](https://github.com/PegaSysEng/pantheon/pull/1757)
- Implemented EIP-2200 - Net Gas Metering Revised [#1743](https://github.com/PegaSysEng/pantheon/pull/1743)
- Added chainId validation to PrivateTransactionValidator [#1741](https://github.com/PegaSysEng/pantheon/pull/1741)
- Reduced intrinsic gas cost [#1739](https://github.com/PegaSysEng/pantheon/pull/1739)
- De-duplicated test blocks data files [#1737](https://github.com/PegaSysEng/pantheon/pull/1737)
- Renamed various EEA methods to priv methods [#1736](https://github.com/PegaSysEng/pantheon/pull/1736) (thanks to [josh-richardson](https://github.com/josh-richardson))
- Permissioning Acceptance Test [#1735](https://github.com/PegaSysEng/pantheon/pull/1735)
 [#1759](https://github.com/PegaSysEng/pantheon/pull/1759)
- Add nonce handling to GenesisState [#1728](https://github.com/PegaSysEng/pantheon/pull/1728)
- Added 100-continue to HTTP [#1727](https://github.com/PegaSysEng/pantheon/pull/1727)
- Fixed get_signerMetrics [#1725](https://github.com/PegaSysEng/pantheon/pull/1725) (thanks to [matkt](https://github.com/matkt))
- Reworked "in-sync" checks [#1720](https://github.com/PegaSysEng/pantheon/pull/1720)
- Added Accounts Permissioning Acceptance Tests [#1719](https://github.com/PegaSysEng/pantheon/pull/1719)
- Added PrivateTransactionValidator to unify logic [#1713](https://github.com/PegaSysEng/pantheon/pull/1713)
- Added JSON-RPC API to report validator block production information [#1687](https://github.com/PegaSysEng/pantheon/pull/1687) (thanks to [matkt](https://github.com/matkt))
- Added Mark Sweep Pruner [#1638](https://github.com/PegaSysEng/pantheon/pull/1638)
- Added the Blake2b F compression function as a precompile in Pantheon [#1614](https://github.com/PegaSysEng/pantheon/pull/1614) (thanks to [iikirilov](https://github.com/iikirilov))
- Documentation updates include:
  - Added CPU requirements [#1734](https://github.com/PegaSysEng/pantheon/pull/1734)
  - Added reference to Ansible role [#1733](https://github.com/PegaSysEng/pantheon/pull/1733)
  - Updated revert reason example [#1754](https://github.com/PegaSysEng/pantheon/pull/1754)
  - Added content on deploying for production [#1774](https://github.com/PegaSysEng/pantheon/pull/1774)
  - Updated docker docs for location of data path [#1790](https://github.com/PegaSysEng/pantheon/pull/1790)
  - Updated permissiong documentation
  [#1792](https://github.com/PegaSysEng/pantheon/pull/1792)
  [#1652](https://github.com/PegaSysEng/pantheon/pull/1652)
  - Added permissioning webinar in the resources [#1717](https://github.com/PegaSysEng/pantheon/pull/1717)
  - Add web3.js-eea reference doc [#1617](https://github.com/PegaSysEng/pantheon/pull/1617)
  - Updated privacy documentation 
  [#1650](https://github.com/PegaSysEng/pantheon/pull/1650)
  [#1721](https://github.com/PegaSysEng/pantheon/pull/1721)
  [#1722](https://github.com/PegaSysEng/pantheon/pull/1722)
  [#1724](https://github.com/PegaSysEng/pantheon/pull/1724)
  [#1729](https://github.com/PegaSysEng/pantheon/pull/1729)
  [#1730](https://github.com/PegaSysEng/pantheon/pull/1730)
  [#1731](https://github.com/PegaSysEng/pantheon/pull/1731)
  [#1732](https://github.com/PegaSysEng/pantheon/pull/1732)
  [#1740](https://github.com/PegaSysEng/pantheon/pull/1740)
  [#1750](https://github.com/PegaSysEng/pantheon/pull/1750)
  [#1761](https://github.com/PegaSysEng/pantheon/pull/1761)
  [#1765](https://github.com/PegaSysEng/pantheon/pull/1765)
  [#1769](https://github.com/PegaSysEng/pantheon/pull/1769)
  [#1770](https://github.com/PegaSysEng/pantheon/pull/1770)
  [#1771](https://github.com/PegaSysEng/pantheon/pull/1771)
  [#1773](https://github.com/PegaSysEng/pantheon/pull/1773)
  [#1787](https://github.com/PegaSysEng/pantheon/pull/1787)
  [#1788](https://github.com/PegaSysEng/pantheon/pull/1788)
  [#1796](https://github.com/PegaSysEng/pantheon/pull/1796)
  [#1803](https://github.com/PegaSysEng/pantheon/pull/1803)
  [#1810](https://github.com/PegaSysEng/pantheon/pull/1810)
  [#1817](https://github.com/PegaSysEng/pantheon/pull/1817)
  - Added documentation for getSignerMetrics [#1723](https://github.com/PegaSysEng/pantheon/pull/1723) (thanks to [matkt](https://github.com/matkt))
  - Added Java 11+ as a prerequisite for installing Pantheon using Homebrew. [#1755](https://github.com/PegaSysEng/pantheon/pull/1755)
  - Fixed documentation formatting and typos [#1718](https://github.com/PegaSysEng/pantheon/pull/1718)
  [#1742](https://github.com/PegaSysEng/pantheon/pull/1742)
  [#1763](https://github.com/PegaSysEng/pantheon/pull/1763)
  [#1779](https://github.com/PegaSysEng/pantheon/pull/1779)
  [#1781](https://github.com/PegaSysEng/pantheon/pull/1781)
  [#1827](https://github.com/PegaSysEng/pantheon/pull/1827)
  [#1767](https://github.com/PegaSysEng/pantheon/pull/1767) (thanks to [helderjnpinto](https://github.com/helderjnpinto))
  - Moved the docs to a [new doc repos](https://github.com/PegaSysEng/doc.pantheon) [#1822](https://github.com/PegaSysEng/pantheon/pull/1822)
- Explicitly configure some maven artifactIds [#1853](https://github.com/PegaSysEng/pantheon/pull/1853)
- Update export subcommand to export blocks in rlp format [#1852](https://github.com/PegaSysEng/pantheon/pull/1852)
- Implement `eth_getproof` JSON RPC API [#1824](https://github.com/PegaSysEng/pantheon/pull/1824)
- Database versioning and enable multi-column database [#1830](https://github.com/PegaSysEng/pantheon/pull/1830)
- Disable Istanbul block [#1849](https://github.com/PegaSysEng/pantheon/pull/)
- Disable smoke tests on windows [#1847](https://github.com/PegaSysEng/pantheon/pull/1847)
- Add read-only blockchain factory method [#1845](https://github.com/PegaSysEng/pantheon/pull/)

## 1.2 

### Additions and Improvements 

- Add UPnP Support [\#1334](https://github.com/PegaSysEng/pantheon/pull/1334) (thanks to [notlesh](https://github.com/notlesh))
- Limit the fraction of wire connections initiated by peers [\#1665](https://github.com/PegaSysEng/pantheon/pull/1665) 
- EIP-1706 - Disable SSTORE with gasleft lt call stipend  [\#1706](https://github.com/PegaSysEng/pantheon/pull/1706) 
- EIP-1108 - Reprice alt\_bn128 [\#1704](https://github.com/PegaSysEng/pantheon/pull/1704) 
- EIP-1344 ChainID Opcode [\#1690](https://github.com/PegaSysEng/pantheon/pull/1690)
- New release docker image [\#1664](https://github.com/PegaSysEng/pantheon/pull/1664) 
- Support changing log level at runtime [\#1656](https://github.com/PegaSysEng/pantheon/pull/1656) (thanks to [matkt](https://github.com/matkt))
- Implement dump command to dump a specific block from storage [\#1641](https://github.com/PegaSysEng/pantheon/pull/1641) (thanks to [matkt](https://github.com/matkt))
- Add eea\_findPrivacyGroup endpoint to Pantheon [\#1635](https://github.com/PegaSysEng/pantheon/pull/1635) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Updated eea send raw transaction with privacy group ID [\#1611](https://github.com/PegaSysEng/pantheon/pull/1611) (thanks to [iikirilov](https://github.com/iikirilov))
- Added Revert Reason [\#1603](https://github.com/PegaSysEng/pantheon/pull/1603)
- Documentation updates include: 
  - Added [UPnP content](https://docs.pantheon.pegasys.tech/en/latest/Configuring-Pantheon/Networking/Using-UPnP/)
  - Added [load balancer image](https://docs.pantheon.pegasys.tech/en/latest/Deploying-Pantheon/High-Availability/) 
  - Added [revert reason](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/Transactions/Revert-Reason/) 
  - Added [admin\_changeLogLevel](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-API-Methods/#admin_changeloglevel) JSON RPC API (thanks to [matkt](https://github.com/matkt))
  - Updated for [new Docker image](https://docs.pantheon.pegasys.tech/en/latest/Getting-Started/Run-Docker-Image/) 
  - Added [Docker image migration content](https://docs.pantheon.pegasys.tech/en/latest/Deploying-Pantheon/Migration-Docker/) 
  - Added [transaction validation content](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/Transactions/Transaction-Validation/) 
  - Updated [permissioning overview](https://docs.pantheon.pegasys.tech/en/latest/Permissions/Permissioning-Overview/) for onchain account permissioning 
  - Updated [quickstart](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Private-Network-Quickstart/#monitoring-nodes-with-prometheus-and-grafana) to include Prometheus and Grafana 
  - Added [remote connections limits options](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#remote-connections-limit-enabled)
  - Updated [web3.js-eea reference](https://docs.pantheon.pegasys.tech/en/latest/Reference/web3js-eea-Methods/) to include privacy group methods 
  - Updated [onchain permissioning to include account permissioning](https://docs.pantheon.pegasys.tech/en/latest/Permissions/Onchain-Permissioning/Onchain-Permissioning/) and [Permissioning Management Dapp](https://docs.pantheon.pegasys.tech/en/latest/Permissions/Onchain-Permissioning/Production/)
  - Added [deployment procedure for Permissioning Management Dapp](https://docs.pantheon.pegasys.tech/en/latest/Permissions/Onchain-Permissioning/Production/) 
  - Added privacy content for [EEA-compliant and Pantheon-extended privacy](https://docs.pantheon.pegasys.tech/en/latest/Privacy/Explanation/Privacy-Groups/) 
  - Added content on [creating and managing privacy groups](https://docs.pantheon.pegasys.tech/en/latest/Privacy/How-To/Create-Manage-Privacy-Groups/)
  - Added content on [accessing private and privacy marker transactions](https://docs.pantheon.pegasys.tech/en/latest/Privacy/How-To/Access-Private-Transactions/)
  - Added content on [system requirements](https://docs.pantheon.pegasys.tech/en/latest/Installation/System-Requirements/)
  - Added reference to [Pantheon role on Galaxy to deploy using Ansible](https://docs.pantheon.pegasys.tech/en/latest/Deploying-Pantheon/Ansible/).  

### Technical Improvements 

- Remove enclave public key from parameter [\#1789](https://github.com/PegaSysEng/pantheon/pull/1789)
- Update defaults host interfaces [\#1782](https://github.com/PegaSysEng/pantheon/pull/1782)
- Modifies PrivGetPrivateTransaction to take public tx hash [\#1778](https://github.com/PegaSysEng/pantheon/pull/1778)
- Remove enclave public key from parameter [\#1777](https://github.com/PegaSysEng/pantheon/pull/1777)
- Return the ethereum address of the privacy precompile from priv_getPrivacyPrecompileAddress [\#1766](https://github.com/PegaSysEng/pantheon/pull/1766)
- Report node local address as the coinbase in Clique and IBFT [\#1760](https://github.com/PegaSysEng/pantheon/pull/1760)
- Additional integration test for contract creation with privacyGroupId [\#1762](https://github.com/PegaSysEng/pantheon/pull/1762)
- Report 0 hashrate when the mining coordinator doesn't support mining [\#1757](https://github.com/PegaSysEng/pantheon/pull/1757)
- Fix private tx signature validation [\#1753](https://github.com/PegaSysEng/pantheon/pull/1753)
- RevertReason changed to BytesValue [\#1746](https://github.com/PegaSysEng/pantheon/pull/1746)
- Renames various eea methods to priv methods [\#1736](https://github.com/PegaSysEng/pantheon/pull/1736)
- Update Orion version [\#1716](https://github.com/PegaSysEng/pantheon/pull/1716)
- Rename CLI flag for better ordering of options [\#1715](https://github.com/PegaSysEng/pantheon/pull/1715)
- Routine dependency updates [\#1712](https://github.com/PegaSysEng/pantheon/pull/1712) 
- Fix spelling error in getApplicationPrefix method name [\#1711](https://github.com/PegaSysEng/pantheon/pull/1711) 
- Wait and retry if best peer's chain is too short for fast sync [\#1708](https://github.com/PegaSysEng/pantheon/pull/1708) 
- Eea get private transaction fix [\#1707](https://github.com/PegaSysEng/pantheon/pull/1707) (thanks to [iikirilov](https://github.com/iikirilov))
- Rework remote connection limit flag defaults [\#1705](https://github.com/PegaSysEng/pantheon/pull/1705)
- Report invalid options from config file [\#1703](https://github.com/PegaSysEng/pantheon/pull/1703) 
- Add ERROR to list of CLI log level options [\#1699](https://github.com/PegaSysEng/pantheon/pull/1699) 
- Enable onchain account permissioning CLI option [\#1686](https://github.com/PegaSysEng/pantheon/pull/1686)
- Exempt static nodes from all connection limits [\#1685](https://github.com/PegaSysEng/pantheon/pull/1685)
- Enclave refactoring [\#1684](https://github.com/PegaSysEng/pantheon/pull/1684) 
- Add opcode and precompiled support for versioning  [\#1683](https://github.com/PegaSysEng/pantheon/pull/1683) 
- Use a percentage instead of fraction for the remote connections percentage CLI option. [\#1682](https://github.com/PegaSysEng/pantheon/pull/1682) 
- Added error msg for calling eth\_sendTransaction [\#1681](https://github.com/PegaSysEng/pantheon/pull/1681) 
- Remove instructions for installing with Chocolatey [\#1680](https://github.com/PegaSysEng/pantheon/pull/1680) 
- remove zulu-jdk8 from smoke tests [\#1679](https://github.com/PegaSysEng/pantheon/pull/1679)
- Add new MainNet bootnodes [\#1678](https://github.com/PegaSysEng/pantheon/pull/1678) 
- updating smoke tests to use \>= jdk11 [\#1677](https://github.com/PegaSysEng/pantheon/pull/1677) 
- Fix handling of remote connection limit [\#1676](https://github.com/PegaSysEng/pantheon/pull/1676) 
- Add accountVersion to MessageFrame [\#1675](https://github.com/PegaSysEng/pantheon/pull/1675) 
- Change getChildren return type [\#1674](https://github.com/PegaSysEng/pantheon/pull/1674) 
- Use Log4J message template instead of String.format [\#1673](https://github.com/PegaSysEng/pantheon/pull/1673) 
- Return hashrate of 0 when not mining. [\#1672](https://github.com/PegaSysEng/pantheon/pull/1672)
- Add hooks for validation  [\#1671](https://github.com/PegaSysEng/pantheon/pull/1671) 
- Upgrade to pantheon-build:0.0.6-jdk11 which really does include jdk11 [\#1670](https://github.com/PegaSysEng/pantheon/pull/1670) 
- Onchain permissioning startup check [\#1669](https://github.com/PegaSysEng/pantheon/pull/1669) 
- Update PantheonCommand to accept minTransactionGasPriceWei as an integer [\#1668](https://github.com/PegaSysEng/pantheon/pull/1668) (thanks to [matkt](https://github.com/matkt))
- Privacy group id consistent [\#1667](https://github.com/PegaSysEng/pantheon/pull/1667) (thanks to [iikirilov](https://github.com/iikirilov))
- Change eea\_getPrivateTransaction endpoint to accept hex [\#1666](https://github.com/PegaSysEng/pantheon/pull/1666) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Factorise metrics code for KeyValueStorage database [\#1663](https://github.com/PegaSysEng/pantheon/pull/1663))
- Create a metric tracking DB size [\#1662](https://github.com/PegaSysEng/pantheon/pull/1662) 
- AT- Removing unused methods on KeyValueStorage [\#1661](https://github.com/PegaSysEng/pantheon/pull/1661) 
- Add Prerequisites and Quick-Start [\#1660](https://github.com/PegaSysEng/pantheon/pull/1660) (thanks to [lazaridiscom](https://github.com/lazaridiscom))
- Java 11 updates [\#1658](https://github.com/PegaSysEng/pantheon/pull/1658)
- Make test generated keys deterministic w/in block generator [\#1657](https://github.com/PegaSysEng/pantheon/pull/1657)
- Rename privacyGroupId to createPrivacyGroupId [\#1654](https://github.com/PegaSysEng/pantheon/pull/1654) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Intermittent Test Failures in TransactionsMessageSenderTest [\#1653](https://github.com/PegaSysEng/pantheon/pull/1653) 
- Sanity check the generated distribution files before upload [\#1648](https://github.com/PegaSysEng/pantheon/pull/1648)
- Use JDK 11 for release builds [\#1647](https://github.com/PegaSysEng/pantheon/pull/1647)
- Support multiple private marker transactions in a block  [\#1646](https://github.com/PegaSysEng/pantheon/pull/1646) 
- Display World State Sync Progress in Logs [\#1645](https://github.com/PegaSysEng/pantheon/pull/1645) 
- Remove the docker gradle plugin, handle building docker with shell now [\#1644](https://github.com/PegaSysEng/pantheon/pull/1644) 
- Switch to using metric names from EIP-2159 [\#1634](https://github.com/PegaSysEng/pantheon/pull/1634) 
- Account versioning [\#1612](https://github.com/PegaSysEng/pantheon/pull/1612) 

## 1.1.4 

### Additions and Improvements

- \[PAN-2832\] Support setting config options via environment variables [\#1597](https://github.com/PegaSysEng/pantheon/pull/1597) 
- Print Pantheon version when starting [\#1593](https://github.com/PegaSysEng/pantheon/pull/1593) 
- \[PAN-2746\] Add eea\_createPrivacyGroup & eea\_deletePrivacyGroup endpoint [\#1560](https://github.com/PegaSysEng/pantheon/pull/1560) (thanks to [Puneetha17](https://github.com/Puneetha17))

Documentation updates include: 
- Added [readiness and liveness endpoints](https://docs.pantheon.pegasys.tech/en/latest/Pantheon-API/Using-JSON-RPC-API/#readiness-and-liveness-endpoints) 
- Added [high availability content](https://docs.pantheon.pegasys.tech/en/latest/Deploying-Pantheon/High-Availability/) 
- Added [web3js-eea client library](https://docs.pantheon.pegasys.tech/en/latest/Privacy/Private-Transactions/eeajs/) 
- Added content on [setting CLI options using environment variables](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#specifying-options)

### Technical Improvements 

- Read config from env vars when no config file specified [\#1639](https://github.com/PegaSysEng/pantheon/pull/1639)
- Upgrade jackson-databind to 2.9.9.1 [\#1636](https://github.com/PegaSysEng/pantheon/pull/1636)
- Update Reference Tests [\#1633](https://github.com/PegaSysEng/pantheon/pull/1633)
- Ignore discport during static node permissioning check [\#1631](https://github.com/PegaSysEng/pantheon/pull/1631)
- Check connections more frequently during acceptance tests [\#1630](https://github.com/PegaSysEng/pantheon/pull/1630)
- Refactor experimental CLI options [\#1629](https://github.com/PegaSysEng/pantheon/pull/1629)
- JSON-RPC api net_services should display the actual ports [\#1628](https://github.com/PegaSysEng/pantheon/pull/1628)
- Refactor CLI [\#1627](https://github.com/PegaSysEng/pantheon/pull/1627) 
- Simplify PantheonCommand `run` and `parse` methods. [\#1626](https://github.com/PegaSysEng/pantheon/pull/1626) 
- PAN-2860: Ignore discport during startup whitelist validation [\#1625](https://github.com/PegaSysEng/pantheon/pull/1625)
- Freeze plugin api version [\#1624](https://github.com/PegaSysEng/pantheon/pull/1624) 
- Implement incoming transaction messages CLI option as an unstable command. [\#1622](https://github.com/PegaSysEng/pantheon/pull/1622) 
- Update smoke tests docker images for zulu and openjdk to private ones [\#1620](https://github.com/PegaSysEng/pantheon/pull/1620) 
- Remove duplication between EeaTransactionCountRpc & PrivateTransactionHandler [\#1619](https://github.com/PegaSysEng/pantheon/pull/1619)
- \[PAN-2709\] - nonce too low error [\#1618](https://github.com/PegaSysEng/pantheon/pull/1618) 
- Cache TransactionValidationParams instead of creating new object for each call [\#1616](https://github.com/PegaSysEng/pantheon/pull/1616) 
- \[PAN-2850\] Create a transaction pool configuration object [\#1615](https://github.com/PegaSysEng/pantheon/pull/1615) 
- Add TransactionValidationParam to TxProcessor [\#1613](https://github.com/PegaSysEng/pantheon/pull/1613) 
- Expose a CLI option to configure the life time of transaction messages. [\#1610](https://github.com/PegaSysEng/pantheon/pull/1610) 
- Implement Prometheus metric counter for skipped expired transaction messages. [\#1609](https://github.com/PegaSysEng/pantheon/pull/1609) 
- Upload jars to bintray as part of releases [\#1608](https://github.com/PegaSysEng/pantheon/pull/1608) 
- Avoid publishing docker-pantheon directory to bintray during a release [\#1606](https://github.com/PegaSysEng/pantheon/pull/1606) 
- \[PAN-2756\] Istanbul scaffolding [\#1605](https://github.com/PegaSysEng/pantheon/pull/1605) 
- Implement a timeout in TransactionMessageProcessor [\#1604](https://github.com/PegaSysEng/pantheon/pull/1604) 
- Reject transactions with gas price below the configured minimum [\#1602](https://github.com/PegaSysEng/pantheon/pull/1602) 
- Always build the k8s image, only push to dockerhub for master branch [\#1601](https://github.com/PegaSysEng/pantheon/pull/1601) 
- Properly validate AltBN128 pairing precompile input [\#1600](https://github.com/PegaSysEng/pantheon/pull/1600) 
- \[PAN-2871\] Columnar rocksdb [\#1599](https://github.com/PegaSysEng/pantheon/pull/1599) 
- Reverting change to dockerfile [\#1594](https://github.com/PegaSysEng/pantheon/pull/1594) 
- Update dependency versions [\#1592](https://github.com/PegaSysEng/pantheon/pull/1592) 
- \[PAN-2797\] Clean up failed connections [\#1591](https://github.com/PegaSysEng/pantheon/pull/1591) 
- Cleaning up the build process for docker [\#1590](https://github.com/PegaSysEng/pantheon/pull/1590) 
- \[PAN-2786\] Stop Transaction Pool Queue from Growing Unbounded [\#1586](https://github.com/PegaSysEng/pantheon/pull/1586) 

## 1.1.3

### Additions and Improvements

- \[PAN-2811\] Be more lenient with discovery message deserialization. Completes our support for EIP-8 and enables Pantheon to work on Rinkeby again. [\#1580](https://github.com/PegaSysEng/pantheon/pull/1580) 
- Added liveness and readiness probe stub endpoints [\#1553](https://github.com/PegaSysEng/pantheon/pull/1553) 
- Implemented operator tool. \(blockchain network configuration for permissioned networks\) [\#1511](https://github.com/PegaSysEng/pantheon/pull/1511) 
- \[PAN-2754\] Added eea\_getPrivacyPrecompileAddress [\#1579](https://github.com/PegaSysEng/pantheon/pull/1579) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Publish the chain head gas used, gas limit, transaction count and ommer metrics [\#1551](https://github.com/PegaSysEng/pantheon/pull/1551)
- Add subscribe and unsubscribe count metrics [\#1541](https://github.com/PegaSysEng/pantheon/pull/1541)
- Add pivot block metrics [\#1537](https://github.com/PegaSysEng/pantheon/pull/1537)

Documentation updates include: 

- Updated [IBFT 2.0 tutorial](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-IBFT-Network/) to use network configuration tool
- Added [debug\_traceBlock\* methods](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-API-Methods/#debug_traceblock) 
- Reorganised [monitoring documentation](https://docs.pantheon.pegasys.tech/en/latest/Monitoring/Monitoring-Performance/)
- Added [link to sample Grafana dashboard](https://docs.pantheon.pegasys.tech/en/latest/Monitoring/Monitoring-Performance/#monitor-node-performance-using-prometheus) 
- Added [note about replacing transactions in transaction pool](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/Transactions/Transaction-Pool/#replacing-transactions-with-same-nonce)
- Updated [example transaction scripts](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/Transactions/Transactions/#example-javascript-scripts)
- Updated [Alethio Ethstats and Explorer documentation](https://docs.pantheon.pegasys.tech/en/latest/Monitoring/Alethio/Overview/)

### Technical Improvements 

- PAN-2816: Hiding experimental account permissioning cli options [\#1584](https://github.com/PegaSysEng/pantheon/pull/1584)
- \[PAN-2630\] Synchronizer should disconnect the sync target peer on invalid block data [\#1578](https://github.com/PegaSysEng/pantheon/pull/1578) 
- Rename MetricCategory to PantheonMetricCategory [\#1574](https://github.com/PegaSysEng/pantheon/pull/1574) 
- Convert MetricsConfigiguration to use a builder [\#1572](https://github.com/PegaSysEng/pantheon/pull/1572) 
- PAN-2794: Including flag for onchain permissioning check on tx processor [\#1571](https://github.com/PegaSysEng/pantheon/pull/1571) 
- Fix behaviour for absent account permissiong smart contract [\#1569](https://github.com/PegaSysEng/pantheon/pull/1569) 
- Expand readiness check to check peer count and sync state [\#1568](https://github.com/PegaSysEng/pantheon/pull/1568) 
- \[PAN-2798\] Reorganize p2p classes [\#1567](https://github.com/PegaSysEng/pantheon/pull/1567) 
- PAN-2729: Account Smart Contract Permissioning ATs [\#1565](https://github.com/PegaSysEng/pantheon/pull/1565) 
- Timeout build after 1 hour to prevent it hanging forever. [\#1564](https://github.com/PegaSysEng/pantheon/pull/1564) 
- \[PAN-2791\] Make permissions checks for ongoing connections more granular [\#1563](https://github.com/PegaSysEng/pantheon/pull/1563) 
- \[PAN-2721\] Fix TopicParameter deserialization [\#1562](https://github.com/PegaSysEng/pantheon/pull/1562) 
- \[PAN-2779\] Allow signing private transaction with any key [\#1561](https://github.com/PegaSysEng/pantheon/pull/1561) (thanks to [iikirilov](https://github.com/iikirilov))
- \[PAN-2783\] Invert dependency between permissioning and p2p [\#1557](https://github.com/PegaSysEng/pantheon/pull/1557) 
- Removing account filter from TransactionPool [\#1556](https://github.com/PegaSysEng/pantheon/pull/1556) 
- \[PAN-1952\] - Remove ignored pending transaction event publish acceptance test [\#1552](https://github.com/PegaSysEng/pantheon/pull/1552) 
- Make MetricCategories more flexible [\#1550](https://github.com/PegaSysEng/pantheon/pull/1550) 
- Fix encoding for account permissioning check call [\#1549](https://github.com/PegaSysEng/pantheon/pull/1549) 
- Discard known remote transactions prior to validation [\#1548](https://github.com/PegaSysEng/pantheon/pull/1548)
- \[PAN-2009\] - Fix cluster clean start after stop in Acceptance tests [\#1546](https://github.com/PegaSysEng/pantheon/pull/1546) 
- FilterIdGenerator fixes [\#1544](https://github.com/PegaSysEng/pantheon/pull/1544) 
- Only increment the added transaction counter if we actually added the transaction [\#1543](https://github.com/PegaSysEng/pantheon/pull/1543) 
- When retrieving transactions by hash, check the pending transactions first [\#1542](https://github.com/PegaSysEng/pantheon/pull/1542) 
- Fix thread safety in SubscriptionManager [\#1540](https://github.com/PegaSysEng/pantheon/pull/1540)
- \[PAN-2731\] Extract connection management from P2PNetwork [\#1538](https://github.com/PegaSysEng/pantheon/pull/1538) 
- \[PAN-2010\] format filter id as quantity [\#1534](https://github.com/PegaSysEng/pantheon/pull/1534)
- PAN-2445: Onchain account permissioning [\#1507](https://github.com/PegaSysEng/pantheon/pull/1507) 
- \[PAN-2672\] Return specific and useful error for enclave issues [\#1455](https://github.com/PegaSysEng/pantheon/pull/1455) (thanks to [Puneetha17](https://github.com/Puneetha17))

## 1.1.2

### Additions and Improvements

Documentation updates include: 

- Added [GraphQL options](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#graphql-http-cors-origins) 
- Added [troubleshooting point about illegal reflective access error](https://docs.pantheon.pegasys.tech/en/latest/Troubleshooting/Troubleshooting/#illegal-reflective-access-error-on-startup)
- Added [trusted bootnode behaviour for permissioning](https://docs.pantheon.pegasys.tech/en/latest/Permissions/Onchain-Permissioning/#bootnodes)
- Added [how to obtain a WS authentication token](https://docs.pantheon.pegasys.tech/en/latest/Pantheon-API/Authentication/#obtaining-an-authentication-token)
- Updated [example scripts and added package.json file for creating signed transactions](https://docs.pantheon.pegasys.tech/en/stable/Using-Pantheon/Transactions/Transactions/)

### Technical Improvements 

- Replaced Void datatype with void [\#1530](https://github.com/PegaSysEng/pantheon/pull/1530) 
- Fix estimate gas RPC failing for clique when no blocks have been created [\#1528](https://github.com/PegaSysEng/pantheon/pull/1528) 
- Avoid auto-boxing for gauge metrics [\#1526](https://github.com/PegaSysEng/pantheon/pull/1526)
- Add AT to ensure 0-miner Clique/IBFT are valid [\#1525](https://github.com/PegaSysEng/pantheon/pull/1525) 
- AT DSL - renaming to suffix of Conditions and co-locating with Conditions [\#1524](https://github.com/PegaSysEng/pantheon/pull/1524) 
- Set disconnect flag immediately when disconnecting a peer [\#1521](https://github.com/PegaSysEng/pantheon/pull/1521) 
- \[PAN-2547\] Modified JSON-RPC subscription processing to avoid blocking [\#1519](https://github.com/PegaSysEng/pantheon/pull/1519) 
- Dependency Version Updates [\#1517](https://github.com/PegaSysEng/pantheon/pull/1517) 
- AT DSL - renaming ibft to ibft2 [\#1516](https://github.com/PegaSysEng/pantheon/pull/1516)
- \[PIE-1578\] Added local transaction permissioning metrics [\#1515](https://github.com/PegaSysEng/pantheon/pull/1515) 
- \[PIE-1577\] Added node local metrics [\#1514](https://github.com/PegaSysEng/pantheon/pull/1514) 
- AT DSL - Removing WaitCondition, consistently applying Condition instead [\#1513](https://github.com/PegaSysEng/pantheon/pull/1513) 
- Remove usage of deprecated ConcurrentSet [\#1512](https://github.com/PegaSysEng/pantheon/pull/1512) 
- Log error if clique or ibft have 0 validators in genesis [\#1509](https://github.com/PegaSysEng/pantheon/pull/1509) 
- GraphQL library upgrade changes. [\#1508](https://github.com/PegaSysEng/pantheon/pull/1508) 
- Add metrics to assist monitoring and alerting [\#1506](https://github.com/PegaSysEng/pantheon/pull/1506) 
- Use external pantheon-plugin-api library [\#1505](https://github.com/PegaSysEng/pantheon/pull/1505) 
- Tilde [\#1504](https://github.com/PegaSysEng/pantheon/pull/1504) 
- Dependency version updates [\#1503](https://github.com/PegaSysEng/pantheon/pull/1503) 
- Simplify text [\#1501](https://github.com/PegaSysEng/pantheon/pull/1501) (thanks to [bgravenorst](https://github.com/bgravenorst))
- \[PAN-1625\] Clique AT mining continues if validator offline [\#1500](https://github.com/PegaSysEng/pantheon/pull/1500) 
- Acceptance Test DSL Node refactoring [\#1498](https://github.com/PegaSysEng/pantheon/pull/1498) 
- Updated an incorrect command [\#1497](https://github.com/PegaSysEng/pantheon/pull/1497) (thanks to [bgravenorst](https://github.com/bgravenorst))
- Acceptance Test and DSL rename for IBFT2 [\#1493](https://github.com/PegaSysEng/pantheon/pull/1493) 
- \[PIE-1580\] Metrics for smart contract permissioning actions [\#1492](https://github.com/PegaSysEng/pantheon/pull/1492) 
- Handle RLPException when processing incoming DevP2P messages [\#1491](https://github.com/PegaSysEng/pantheon/pull/1491)
- Limit spotless checks to java classes in expected java  dirs [\#1490](https://github.com/PegaSysEng/pantheon/pull/1490) 
- \[PAN-2560\] Add LocalNode class [\#1489](https://github.com/PegaSysEng/pantheon/pull/1489) 
- Changed Enode length error String implementation. [\#1486](https://github.com/PegaSysEng/pantheon/pull/1486)
- PAN-2715 - return block not found reasons in error [\#1485](https://github.com/PegaSysEng/pantheon/pull/1485)
- \[PAN-2652\] Refactor Privacy acceptance test and add Privacy Ibft test [\#1483](https://github.com/PegaSysEng/pantheon/pull/1483) (thanks to [iikirilov](https://github.com/iikirilov))
- \[PAN-2603\] Onchain account permissioning support [\#1475](https://github.com/PegaSysEng/pantheon/pull/1475) 
- Make CLI options names with hyphen-minus searchable and reduce index size [\#1476](https://github.com/PegaSysEng/pantheon/pull/1476)
- Added warning banner when using latest version [\#1454](https://github.com/PegaSysEng/pantheon/pull/1454)
- Add RTD config file to fix Python version issue [\#1453](https://github.com/PegaSysEng/pantheon/pull/1453) 
- \[PAN-2647\] Validate Private Transaction nonce before submitting to Transaction Pool [\#1449](https://github.com/PegaSysEng/pantheon/pull/1449) (thanks to [iikirilov](https://github.com/iikirilov))
- Add placeholders system to have global variables in markdown [\#1425](https://github.com/PegaSysEng/pantheon/pull/1425) 

## 1.1.1 

### Additions and Improvements 

- [GraphQL](https://docs.pantheon.pegasys.tech/en/latest/Pantheon-API/GraphQL/) [\#1311](https://github.com/PegaSysEng/pantheon/pull/1311) (thanks to [zyfrank](https://github.com/zyfrank))
- Added [`--tx-pool-retention-hours`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#tx-pool-retention-hours) [\#1333](https://github.com/PegaSysEng/pantheon/pull/1333)
- Added Genesis file support for specifying the maximum stack size. [\#1431](https://github.com/PegaSysEng/pantheon/pull/1431) 
- Included transaction details when subscribed to Pending transactions [\#1410](https://github.com/PegaSysEng/pantheon/pull/1410) 
- Documentation updates include:
  - [Added configuration items specified in the genesis file](https://docs.pantheon.pegasys.tech/en/latest/Configuring-Pantheon/Config-Items/)  
  - [Added pending transaction details subscription](https://docs.pantheon.pegasys.tech/en/latest/Pantheon-API/RPC-PubSub/#pending-transactions) 
  - [Added Troubleshooting content](https://docs.pantheon.pegasys.tech/en/latest/Troubleshooting/Troubleshooting/)
  - [Added Privacy Quickstart](https://docs.pantheon.pegasys.tech/en/latest/Privacy/Privacy-Quickstart/)  
  - [Added privacy roadmap](https://github.com/PegaSysEng/pantheon/blob/master/PRIVACYROADMAP.MD)  


### Technical Improvements 

- Create MaintainedPeers class [\#1484](https://github.com/PegaSysEng/pantheon/pull/1484) 
- Fix for permissioned network with single bootnode [\#1479](https://github.com/PegaSysEng/pantheon/pull/1479) 
- Have ThreadPantheonNodeRunner support plugin tests [\#1477](https://github.com/PegaSysEng/pantheon/pull/1477) 
- Less pointless plugins errors [\#1473](https://github.com/PegaSysEng/pantheon/pull/1473) 
- Rename GraphQLRPC to just GraphQL [\#1472](https://github.com/PegaSysEng/pantheon/pull/1472) 
- eth\_protocolVersion is a Quantity, not an Integer [\#1470](https://github.com/PegaSysEng/pantheon/pull/1470) 
- Don't require 'to' in 'blocks' queries [\#1464](https://github.com/PegaSysEng/pantheon/pull/1464) 
- Events Plugin - Add initial "NewBlock" event message [\#1463](https://github.com/PegaSysEng/pantheon/pull/1463) 
- Make restriction field in Private Transaction an enum [\#1462](https://github.com/PegaSysEng/pantheon/pull/1462) (thanks to [iikirilov](https://github.com/iikirilov))
- Helpful graphql error when an account doesn't exist [\#1460](https://github.com/PegaSysEng/pantheon/pull/1460) 
- Acceptance Test Cleanup [\#1458](https://github.com/PegaSysEng/pantheon/pull/1458) 
- Large chain id support for private transactions [\#1452](https://github.com/PegaSysEng/pantheon/pull/1452) 
- Optimise TransactionPool.addRemoteTransaction [\#1448](https://github.com/PegaSysEng/pantheon/pull/1448) 
- Reduce synchronization in PendingTransactions [\#1447](https://github.com/PegaSysEng/pantheon/pull/1447) 
- Add simple PeerPermissions interface [\#1446](https://github.com/PegaSysEng/pantheon/pull/1446) 
- Make sure ThreadPantheonNodeRunner is exercised by automation [\#1442](https://github.com/PegaSysEng/pantheon/pull/1442) 
- Decode devp2p packets off the event thread [\#1439](https://github.com/PegaSysEng/pantheon/pull/1439) 
- Allow config files to specify no bootnodes [\#1438](https://github.com/PegaSysEng/pantheon/pull/1438) 
- Capture all logs and errors in the Pantheon log output [\#1437](https://github.com/PegaSysEng/pantheon/pull/1437) 
- Ensure failed Txns are deleted when detected during mining [\#1436](https://github.com/PegaSysEng/pantheon/pull/1436)
- Plugin Framework [\#1435](https://github.com/PegaSysEng/pantheon/pull/1435) 
- Equals cleanup [\#1434](https://github.com/PegaSysEng/pantheon/pull/1434) 
- Transaction smart contract permissioning controller [\#1433](https://github.com/PegaSysEng/pantheon/pull/1433) 
- Renamed AccountPermissioningProver to TransactionPermissio… [\#1432](https://github.com/PegaSysEng/pantheon/pull/1432) 
- Refactorings and additions to add Account based Smart Contract permissioning [\#1430](https://github.com/PegaSysEng/pantheon/pull/1430) 
- Fix p2p PeerInfo handling [\#1428](https://github.com/PegaSysEng/pantheon/pull/1428) 
- IbftProcessor logs when a throwable terminates mining [\#1427](https://github.com/PegaSysEng/pantheon/pull/1427) 
- Renamed AccountWhitelistController [\#1424](https://github.com/PegaSysEng/pantheon/pull/1424) 
- Unwrap DelegatingBytes32 and prevent Hash from wrapping other Hash instances [\#1423](https://github.com/PegaSysEng/pantheon/pull/1423) 
- If nonce is invalid, do not delete during mining [\#1422](https://github.com/PegaSysEng/pantheon/pull/1422) 
- Deleting unused windows jenkinsfile [\#1421](https://github.com/PegaSysEng/pantheon/pull/1421) 
- Get all our smoke tests for all platforms in 1 jenkins job [\#1420](https://github.com/PegaSysEng/pantheon/pull/1420)
- Add pending object to GraphQL queries [\#1419](https://github.com/PegaSysEng/pantheon/pull/1419)
- Start listening for p2p connections after start\(\) is invoked [\#1418](https://github.com/PegaSysEng/pantheon/pull/1418) 
- Improved JSON-RPC responses when EnodeURI parameter has invalid EnodeId [\#1417](https://github.com/PegaSysEng/pantheon/pull/1417) 
- Use port 0 when starting a websocket server in tests [\#1416](https://github.com/PegaSysEng/pantheon/pull/1416)
- Windows jdk smoke tests [\#1413](https://github.com/PegaSysEng/pantheon/pull/1413) 
- Change AT discard RPC tests to be more reliable by checking discard using proposals [\#1411](https://github.com/PegaSysEng/pantheon/pull/1411) 
- Simple account permissioning [\#1409](https://github.com/PegaSysEng/pantheon/pull/1409) 
- Fix clique miner to respect changes to vanity data made via JSON-RPC [\#1408](https://github.com/PegaSysEng/pantheon/pull/1408) 
- Avoid recomputing the logs bloom filter when reading receipts [\#1407](https://github.com/PegaSysEng/pantheon/pull/1407) 
- Remove NodePermissioningLocalConfig external references [\#1406](https://github.com/PegaSysEng/pantheon/pull/1406) 
- Add constantinople fix block for Rinkeby [\#1404](https://github.com/PegaSysEng/pantheon/pull/1404) 
- Update EnodeURL to support enodes with listening disabled [\#1403](https://github.com/PegaSysEng/pantheon/pull/1403) 
- Integration Integration test\(s\) on p2p of 'net\_services'  [\#1402](https://github.com/PegaSysEng/pantheon/pull/1402) 
- Reference tests fail on Windows [\#1401](https://github.com/PegaSysEng/pantheon/pull/1401)
- Fix non-deterministic test caused by variable size of generated transactions [\#1399](https://github.com/PegaSysEng/pantheon/pull/1399) 
- Start BlockPropagationManager immediately - don't wait for full sync [\#1398](https://github.com/PegaSysEng/pantheon/pull/1398) 
- Added error message for RPC method disabled [\#1396](https://github.com/PegaSysEng/pantheon/pull/1396) 
- Fix intermittency in FullSyncChainDownloaderTest [\#1394](https://github.com/PegaSysEng/pantheon/pull/1394) 
- Add explanatory comment about default port [\#1392](https://github.com/PegaSysEng/pantheon/pull/1392) 
- Handle case where peers advertise a listening port of 0 [\#1391](https://github.com/PegaSysEng/pantheon/pull/1391) 
- Cache extra data [\#1389](https://github.com/PegaSysEng/pantheon/pull/1389)
- Update Log message in IBFT Controller [\#1387](https://github.com/PegaSysEng/pantheon/pull/1387)
- Remove unnecessary field [\#1384](https://github.com/PegaSysEng/pantheon/pull/1384) 
- Add getPeer method to PeerConnection [\#1383](https://github.com/PegaSysEng/pantheon/pull/1383) 
- Removing smart quotes [\#1381](https://github.com/PegaSysEng/pantheon/pull/1381) (thanks to [jmcnevin](https://github.com/jmcnevin))
- Use streams and avoid iterating child nodes multiple times [\#1380](https://github.com/PegaSysEng/pantheon/pull/1380) 
- Use execute instead of submit so unhandled exceptions get logged [\#1379](https://github.com/PegaSysEng/pantheon/pull/1379) 
- Prefer EnodeURL over Endpoint [\#1378](https://github.com/PegaSysEng/pantheon/pull/1378)
- Add flat file based task collection [\#1377](https://github.com/PegaSysEng/pantheon/pull/1377)
- Consolidate local enode representation [\#1376](https://github.com/PegaSysEng/pantheon/pull/1376) 
- Rename rocksdDbConfiguration to rocksDbConfiguration [\#1375](https://github.com/PegaSysEng/pantheon/pull/1375) 
- Remove EthTaskChainDownloader and supporting code [\#1373](https://github.com/PegaSysEng/pantheon/pull/1373) 
- Handle the pipeline being aborted while finalizing an async operation [\#1372](https://github.com/PegaSysEng/pantheon/pull/1372) 
- Rename methods that create and return streams away from getX\(\) [\#1368](https://github.com/PegaSysEng/pantheon/pull/1368) 
- eea\_getTransactionCount fails if account has not interacted with private state [\#1367](https://github.com/PegaSysEng/pantheon/pull/1367) (thanks to [iikirilov](https://github.com/iikirilov))
- Increase RocksDB settings [\#1364](https://github.com/PegaSysEng/pantheon/pull/1364) ([ajsutton](https://github.com/ajsutton))
- Don't abort in-progress master builds when a new commit is added. [\#1358](https://github.com/PegaSysEng/pantheon/pull/1358) 
- Request open ended headers from sync target [\#1355](https://github.com/PegaSysEng/pantheon/pull/1355) 
- Enable the pipeline chain downloader by default [\#1344](https://github.com/PegaSysEng/pantheon/pull/1344) 
- Create P2PNetwork Builder [\#1343](https://github.com/PegaSysEng/pantheon/pull/1343)
- Include static nodes in permissioning logic [\#1339](https://github.com/PegaSysEng/pantheon/pull/1339)
- JsonRpcError decoding to include message [\#1336](https://github.com/PegaSysEng/pantheon/pull/1336)
- Cache current chain head info [\#1335](https://github.com/PegaSysEng/pantheon/pull/1335) 
- Queue pending requests when all peers are busy [\#1331](https://github.com/PegaSysEng/pantheon/pull/1331) 
- Fix failed tests on Windows [\#1332](https://github.com/PegaSysEng/pantheon/pull/1332) 
- Provide error message when invalid key specified in key file [\#1328](https://github.com/PegaSysEng/pantheon/pull/1328) 
- Allow whitespace in file paths loaded from resources directory [\#1329](https://github.com/PegaSysEng/pantheon/pull/1329) 
- Allow whitespace in path [\#1327](https://github.com/PegaSysEng/pantheon/pull/1327)
- Require block numbers for debug\_traceBlockByNumber to be in hex [\#1326](https://github.com/PegaSysEng/pantheon/pull/1326) 
- Improve logging of chain download errors in the pipeline chain downloader [\#1325](https://github.com/PegaSysEng/pantheon/pull/1325) 
- Ensure eth scheduler is stopped in tests [\#1324](https://github.com/PegaSysEng/pantheon/pull/1324) 
- Normalize account permissioning addresses in whitelist [\#1321](https://github.com/PegaSysEng/pantheon/pull/1321) 
- Allow private contract invocations in multiple privacy groups [\#1318](https://github.com/PegaSysEng/pantheon/pull/1318) (thanks to [iikirilov](https://github.com/iikirilov))
- Fix account permissioning check case matching [\#1315](https://github.com/PegaSysEng/pantheon/pull/1315) 
- Use header validation mode for ommers [\#1313](https://github.com/PegaSysEng/pantheon/pull/1313) 
- Configure RocksDb max background compaction and thread count [\#1312](https://github.com/PegaSysEng/pantheon/pull/1312) 
- Missing p2p info when queried live [\#1310](https://github.com/PegaSysEng/pantheon/pull/1310) 
- Tx limit size send peers follow up [\#1308](https://github.com/PegaSysEng/pantheon/pull/1308) 
- Remove remnants of the old dev mode [\#1307](https://github.com/PegaSysEng/pantheon/pull/1307) 
- Remove duplicate init code from PantheonController instances [\#1305](https://github.com/PegaSysEng/pantheon/pull/1305) 
- Stop synchronizer prior to stopping the network [\#1302](https://github.com/PegaSysEng/pantheon/pull/1302) 
- Evict old transactions [\#1299](https://github.com/PegaSysEng/pantheon/pull/1299) 
- Send local transactions to new peers [\#1253](https://github.com/PegaSysEng/pantheon/pull/1253) 

## 1.1 

### Additions and Improvements 

- [Privacy](https://docs.pantheon.pegasys.tech/en/latest/Privacy/Privacy-Overview/) 
- [Onchain Permissioning](https://docs.pantheon.pegasys.tech/en/latest/Permissions/Onchain-Permissioning/)
- [Fastsync](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#fast-sync-options) 
- Documentation updates include: 
    - Added JSON-RPC methods: 
      - [`txpool_pantheonStatistics`](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/#txpool_pantheonstatistics)
      - [`net_services`](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/#net_services)
    - [Updated to indicate Docker image doesn't run on Windows](https://docs.pantheon.pegasys.tech/en/latest/Getting-Started/Run-Docker-Image/)
    - [Added how to configure a free gas network](https://docs.pantheon.pegasys.tech/en/latest/Configuring-Pantheon/FreeGas/) 

### Technical Improvements 

- priv_getTransactionCount fails if account has not interacted with private state [\#1369](https://github.com/PegaSysEng/pantheon/pull/1369) 
- Updating Orion to 0.9.0 [\#1360](https://github.com/PegaSysEng/pantheon/pull/1360)
- Allow use of large chain IDs [\#1357](https://github.com/PegaSysEng/pantheon/pull/1357)
- Allow private contract invocations in multiple privacy groups [\#1340](https://github.com/PegaSysEng/pantheon/pull/1340)
- Missing p2p info when queried live [\#1338](https://github.com/PegaSysEng/pantheon/pull/1338)
- Fix expose transaction statistics [\#1337](https://github.com/PegaSysEng/pantheon/pull/1337)
- Normalize account permissioning addresses in whitelist [\#1321](https://github.com/PegaSysEng/pantheon/pull/1321)
- Update Enclave executePost method [\#1319](https://github.com/PegaSysEng/pantheon/pull/1319)
- Fix account permissioning check case matching [\#1315](https://github.com/PegaSysEng/pantheon/pull/1315)
- Removing 'all' from the help wording for host-whitelist [\#1304](https://github.com/PegaSysEng/pantheon/pull/1304)

## 1.1 RC 

### Technical Improvements 

- Better errors for when permissioning contract is set up wrong [\#1296](https://github.com/PegaSysEng/pantheon/pull/1296) 
- Consolidate p2p node info methods [\#1288](https://github.com/PegaSysEng/pantheon/pull/1288) 
- Update permissioning smart contract interface to match updated EEA proposal [\#1287](https://github.com/PegaSysEng/pantheon/pull/1287) 
- Switch to new sync target if it exceeds the td threshold [\#1286](https://github.com/PegaSysEng/pantheon/pull/1286) 
- Fix running ATs with in-process node runner [\#1285](https://github.com/PegaSysEng/pantheon/pull/1285) 
- Simplify enode construction [\#1283](https://github.com/PegaSysEng/pantheon/pull/1283) 
- Cleanup PeerConnection interface [\#1282](https://github.com/PegaSysEng/pantheon/pull/1282) 
- Undo changes to PendingTransactions method visibility [\#1281](https://github.com/PegaSysEng/pantheon/pull/1281) 
- Use default enclave public key to generate eea_getTransactionReceipt [\#1280](https://github.com/PegaSysEng/pantheon/pull/1280) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Rollback to rocksdb 5.15.10 [\#1279](https://github.com/PegaSysEng/pantheon/pull/1279) 
- Log error when a JSON decode problem is encountered [\#1278](https://github.com/PegaSysEng/pantheon/pull/1278) 
- Create EnodeURL builder [\#1275](https://github.com/PegaSysEng/pantheon/pull/1275) 
- Keep enode nodeId stored as a BytesValue [\#1274](https://github.com/PegaSysEng/pantheon/pull/1274) 
- Feature/move subclass in pantheon command [\#1272](https://github.com/PegaSysEng/pantheon/pull/1272) 
- Expose sync mode option [\#1270](https://github.com/PegaSysEng/pantheon/pull/1270) 
- Refactor RocksDBStats [\#1266](https://github.com/PegaSysEng/pantheon/pull/1266) 
- Normalize EnodeURLs [\#1264](https://github.com/PegaSysEng/pantheon/pull/1264) 
- Build broken in Java 12 [\#1263](https://github.com/PegaSysEng/pantheon/pull/1263) 
- Make PeerDiscovertAgentTest less flakey [\#1262](https://github.com/PegaSysEng/pantheon/pull/1262) 
- Ignore extra json rpc params [\#1261](https://github.com/PegaSysEng/pantheon/pull/1261) 
- Fetch local transactions in isolation [\#1259](https://github.com/PegaSysEng/pantheon/pull/1259) 
- Update to debug trace transaction [\#1258](https://github.com/PegaSysEng/pantheon/pull/1258) 
- Use labelled timer to differentiate between rocks db metrics [\#1254](https://github.com/PegaSysEng/pantheon/pull/1254) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Migrate TransactionPool (& affiliated test) from 'core' to 'eth' [\#1251](https://github.com/PegaSysEng/pantheon/pull/1251) 
- Use single instance of Rocksdb for privacy [\#1247](https://github.com/PegaSysEng/pantheon/pull/1247) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Subscribing to sync events should receive false when in sync [\#1240](https://github.com/PegaSysEng/pantheon/pull/1240) 
- Ignore transactions from the network while behind chain head [\#1228](https://github.com/PegaSysEng/pantheon/pull/1228) 
- RocksDB Statistics in Metrics [\#1169](https://github.com/PegaSysEng/pantheon/pull/1169) 
- Add block trace RPC methods [\#1088](https://github.com/PegaSysEng/pantheon/pull/1088) (thanks to [kziemianek](https://github.com/kziemianek))

## 1.0.3

### Additions and Improvements 

- Notify of dropped messages [\#1156](https://github.com/PegaSysEng/pantheon/pull/1156) 
- Documentation updates include: 
    - Added [Permissioning Overview](https://docs.pantheon.pegasys.tech/en/latest/Permissions/Permissioning-Overview/)
    - Added content on [Network vs Node Configuration](https://docs.pantheon.pegasys.tech/en/latest/Configuring-Pantheon/Using-Configuration-File/)   
    - Updated [RAM requirements](https://docs.pantheon.pegasys.tech/en/latest/Installation/Overview/)  
    - Added [Privacy Overview](https://docs.pantheon.pegasys.tech/en/latest/Privacy/Privacy-Overview/) and [Processing Private Transactions](https://docs.pantheon.pegasys.tech/en/latest/Privacy/Private-Transaction-Processing/)
    - Renaming of Ethstats Lite Explorer to [Ethereum Lite Explorer](https://docs.pantheon.pegasys.tech/en/latest/EthStats/Lite-Block-Explorer/) (thanks to [tzapu](https://github.com/tzapu))
    - Added content on using [Truffle with Pantheon](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/Truffle/)
    - Added [`droppedPendingTransactions` RPC Pub/Sub subscription](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/RPC-PubSub/#dropped-transactions) 
    - Added [`eea_*` JSON-RPC API methods](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/#eea-methods)  
    - Added [architecture diagram](https://docs.pantheon.pegasys.tech/en/latest/Architecture/Overview/) 
    - Updated [permissioning CLI options](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#permissions-accounts-config-file-enabled) and [permissioned network tutorial](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-Permissioned-Network/)  

### Technical Improvements 

- Choose sync target based on td rather than height [\#1256](https://github.com/PegaSysEng/pantheon/pull/1256)
- CLI ewp options [\#1246](https://github.com/PegaSysEng/pantheon/pull/1246)
- Update PantheonCommand.java [\#1245](https://github.com/PegaSysEng/pantheon/pull/1245)
- Reduce memory usage in import [\#1239](https://github.com/PegaSysEng/pantheon/pull/1239)
- Improve eea_sendRawTransaction error messages [\#1238](https://github.com/PegaSysEng/pantheon/pull/1238) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Single topic filter [\#1235](https://github.com/PegaSysEng/pantheon/pull/1235)
- Enable pipeline chain downloader for fast sync [\#1232](https://github.com/PegaSysEng/pantheon/pull/1232)
- Make contract size limit configurable [\#1227](https://github.com/PegaSysEng/pantheon/pull/1227)
- Refactor PrivacyParameters config to use builder pattern [\#1226](https://github.com/PegaSysEng/pantheon/pull/1226) (thanks to [antonydenyer](https://github.com/antonydenyer))
- Different request limits for different request types [\#1224](https://github.com/PegaSysEng/pantheon/pull/1224)
- Finish off fast sync pipeline download [\#1222](https://github.com/PegaSysEng/pantheon/pull/1222) 
- Enable fast-sync options on command line [\#1218](https://github.com/PegaSysEng/pantheon/pull/1218) 
- Replace filtering headers after the fact with calculating number to request up-front [\#1216](https://github.com/PegaSysEng/pantheon/pull/1216) 
- Support async processing while maintaining output order [\#1215](https://github.com/PegaSysEng/pantheon/pull/1215) 
- Add Unstable Options to the CLI [\#1213](https://github.com/PegaSysEng/pantheon/pull/1213) 
- Add private cluster acceptance tests [\#1211](https://github.com/PegaSysEng/pantheon/pull/1211) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Re-aligned smart contract interface to EEA client spec 477 [\#1209](https://github.com/PegaSysEng/pantheon/pull/1209) 
- Count the number of items discarded when a pipe is aborted [\#1208](https://github.com/PegaSysEng/pantheon/pull/1208) 
- Pipeline chain download - fetch and import data [\#1207](https://github.com/PegaSysEng/pantheon/pull/1207) 
- Permission provider that allows bootnodes if you have no other connections [\#1206](https://github.com/PegaSysEng/pantheon/pull/1206) 
- Cancel in-progress async operations when the pipeline is aborted [\#1205](https://github.com/PegaSysEng/pantheon/pull/1205) 
- Pipeline chain download - Checkpoints [\#1203](https://github.com/PegaSysEng/pantheon/pull/1203) 
- Push development images to public dockerhub [\#1202](https://github.com/PegaSysEng/pantheon/pull/1202) 
- Push builds of master as docker development images [\#1200](https://github.com/PegaSysEng/pantheon/pull/1200) 
- Doc CI pipeline for build and tests [\#1199](https://github.com/PegaSysEng/pantheon/pull/1199) 
- Replace the use of a disconnect listener with EthPeer.isDisconnected [\#1197](https://github.com/PegaSysEng/pantheon/pull/1197) 
- Prep chain downloader for branch by abstraction [\#1194](https://github.com/PegaSysEng/pantheon/pull/1194) 
- Maintain the state of MessageFrame in private Tx [\#1193](https://github.com/PegaSysEng/pantheon/pull/1193) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Persist private world state only if we are mining [\#1191](https://github.com/PegaSysEng/pantheon/pull/1191) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Remove SyncState from SyncTargetManager [\#1188](https://github.com/PegaSysEng/pantheon/pull/1188) 
- Acceptance tests base for smart contract node permissioning [\#1186](https://github.com/PegaSysEng/pantheon/pull/1186) 
- Fix metrics breakages [\#1185](https://github.com/PegaSysEng/pantheon/pull/1185) 
- Typo [\#1184](https://github.com/PegaSysEng/pantheon/pull/1184) (thanks to [araskachoi](https://github.com/araskachoi))
- StaticNodesParserTest to pass on Windows [\#1183](https://github.com/PegaSysEng/pantheon/pull/1183) 
- Don't mark world state as stalled until a minimum time without progress is reached [\#1179](https://github.com/PegaSysEng/pantheon/pull/1179) 
- Use header validation policy in DownloadHeaderSequenceTask [\#1172](https://github.com/PegaSysEng/pantheon/pull/1172) 
- Bond with bootnodes [\#1160](https://github.com/PegaSysEng/pantheon/pull/1160) 

## 1.0.2 

### Additions and Improvements

- Removed DB init when using `public-key` subcommand [\#1049](https://github.com/PegaSysEng/pantheon/pull/1049) 
- Output enode URL on startup [\#1137](https://github.com/PegaSysEng/pantheon/pull/1137) 
- Added Remove Peer JSON-RPC [\#1129](https://github.com/PegaSysEng/pantheon/pull/1129) 
- Added `net_enode` JSON-RPC [\#1119](https://github.com/PegaSysEng/pantheon/pull/1119) (thanks to [mbergstrand](https://github.com/mbergstrand))
- Maintain a `staticnodes.json` [\#1106](https://github.com/PegaSysEng/pantheon/pull/1106) 
- Added `tx-pool-max-size` command line parameter [\#1078](https://github.com/PegaSysEng/pantheon/pull/1078) 
- Added PendingTransactions JSON-RPC [\#1043](https://github.com/PegaSysEng/pantheon/pull/1043) (thanks to [EdwinLeeGreene](https://github.com/EdwinLeeGreene))
- Added `admin_nodeInfo` JSON-RPC [\#1012](https://github.com/PegaSysEng/pantheon/pull/1012) 
- Added `--metrics-category` CLI to only enable select metrics [\#969](https://github.com/PegaSysEng/pantheon/pull/969) 
- Documentation updates include: 
   - Updated endpoints in [Private Network Quickstart](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Private-Network-Quickstart/) (thanks to [laubai](https://github.com/laubai))
   - Updated [documentation contribution guidelines](https://github.com/PegaSysEng/pantheon/blob/master/DOC-STYLE-GUIDE.md) 
   - Added [`admin_removePeer`](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/#admin_removepeer) 
   - Updated [tutorials](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-Private-Clique-Network/) for printing of enode on startup 
   - Added [`txpool_pantheonTransactions`](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/#txpool_pantheontransactions) 
   - Added [Transaction Pool content](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/Transactions/Transaction-Pool/) 
   - Added [`tx-pool-max-size` CLI option](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#tx-pool-max-size) 
   - Updated [developer build instructions to use installDist](https://github.com/PegaSysEng/pantheon/blob/master/docs/development/running-developer-builds.md) 
   - Added [Azure quickstart tutorial](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Azure/Azure-Private-Network-Quickstart/) 
   - Enabled copy button in code blocks 
   - Added [IBFT 1.0](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/QuorumIBFT/) 
   - Added section on using [Geth attach with Pantheon](https://docs.pantheon.pegasys.tech/en/latest/JSON-RPC-API/Using-JSON-RPC-API/#geth-console)    
   - Enabled the edit link doc site to ease external doc contributions 
   - Added [EthStats docs](https://docs.pantheon.pegasys.tech/en/latest/EthStats/Overview/) (thanks to [baxy](https://github.com/baxy))
   - Updated [Postman collection](https://docs.pantheon.pegasys.tech/en/latest/JSON-RPC-API/Using-JSON-RPC-API/#postman)  
   - Added [`metrics-category` CLI option](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#metrics-category) 
   - Added information on [block time and timeout settings](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/IBFT/#block-time) for IBFT 2.0 
   - Added [`admin_nodeInfo`](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/#admin_nodeinfo) 
   - Added [permissions images](https://docs.pantheon.pegasys.tech/en/latest/Permissions/Permissioning/) 
   - Added permissioning blog to [Resources](https://docs.pantheon.pegasys.tech/en/latest/Resources/Resources/) 
   - Updated [Create Permissioned Network](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-Permissioned-Network/) tutorial to use `export-address` 
   - Updated [Clique](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/Clique/) and [IBFT 2.0](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/IBFT/) docs to include complete genesis file  
   - Updated [Clique tutorial](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-Private-Clique-Network/) to use `export-address` subcommand  
   - Added IBFT 2.0 [future message configuration options](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/IBFT/#optional-configuration-options) 
 
### Technical Improvements
- Fixed so self persists to the whitelist [\#1176](https://github.com/PegaSysEng/pantheon/pull/1176)
- Fixed to add self to permissioning whitelist [\#1175](https://github.com/PegaSysEng/pantheon/pull/1175)
- Fixed permissioning issues [\#1174](https://github.com/PegaSysEng/pantheon/pull/1174)
- AdminAddPeer returns custom Json RPC error code [\#1171](https://github.com/PegaSysEng/pantheon/pull/1171)
- Periodically connect to peers from table [\#1170](https://github.com/PegaSysEng/pantheon/pull/1170)
- Improved bootnodes option error message [\#1092](https://github.com/PegaSysEng/pantheon/pull/1092) 
- Automatically restrict trailing peers while syncing [\#1167](https://github.com/PegaSysEng/pantheon/pull/1167) 
- Avoid bonding to ourselves [\#1166](https://github.com/PegaSysEng/pantheon/pull/1166) 
- Fix Push Metrics [\#1164](https://github.com/PegaSysEng/pantheon/pull/1164) 
- Synchroniser waits for new peer if best is up to date [\#1161](https://github.com/PegaSysEng/pantheon/pull/1161) 
- Don't attempt to download checkpoint headers if the number of headers is negative [\#1158](https://github.com/PegaSysEng/pantheon/pull/1158) 
- Capture metrics on Vertx event loop and worker thread queues [\#1155](https://github.com/PegaSysEng/pantheon/pull/1155) 
- Simplify node permissioning ATs [\#1153](https://github.com/PegaSysEng/pantheon/pull/1153) 
- Add metrics around discovery process [\#1152](https://github.com/PegaSysEng/pantheon/pull/1152) 
- Prevent connecting to self [\#1150](https://github.com/PegaSysEng/pantheon/pull/1150) 
- Refactoring permissioning ATs [\#1148](https://github.com/PegaSysEng/pantheon/pull/1148) 
- Added two extra Ropsten bootnodes [\#1147](https://github.com/PegaSysEng/pantheon/pull/1147) 
- Fixed TCP port handling [\#1144](https://github.com/PegaSysEng/pantheon/pull/1144) 
- Better error on bad header [\#1143](https://github.com/PegaSysEng/pantheon/pull/1143) 
- Refresh peer table while we have fewer than maxPeers connected [\#1142](https://github.com/PegaSysEng/pantheon/pull/1142) 
- Refactor jsonrpc consumption of local node permissioning controller [\#1140](https://github.com/PegaSysEng/pantheon/pull/1140) 
- Disconnect peers before the pivot block while fast syncing [\#1139](https://github.com/PegaSysEng/pantheon/pull/1139) 
- Reduce the default transaction pool size from 30,000 to 4096 [\#1136](https://github.com/PegaSysEng/pantheon/pull/1136) 
- Fail at load if static nodes not whitelisted [\#1135](https://github.com/PegaSysEng/pantheon/pull/1135) 
- Fix private transaction acceptance test [\#1134](https://github.com/PegaSysEng/pantheon/pull/1134) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Quieter exceptions when network is unreachable [\#1133](https://github.com/PegaSysEng/pantheon/pull/1133) 
- nodepermissioningcontroller used for devp2p connection filtering [\#1132](https://github.com/PegaSysEng/pantheon/pull/1132) 
- Remove duplicates from apis specified via CLI [\#1131](https://github.com/PegaSysEng/pantheon/pull/1131) 
- Synchronizer returns false if it is in sync [\#1130](https://github.com/PegaSysEng/pantheon/pull/1130) 
- Added fromHexStringStrict to check for exactly 20 byte addresses [\#1128](https://github.com/PegaSysEng/pantheon/pull/1128) 
- Fix deadlock scenario in AsyncOperationProcessor and re-enable WorldStateDownloaderTest [\#1126](https://github.com/PegaSysEng/pantheon/pull/1126) 
- Ignore WorldStateDownloaderTest [\#1125](https://github.com/PegaSysEng/pantheon/pull/1125) 
- Updated local config permissioning flags [\#1118](https://github.com/PegaSysEng/pantheon/pull/1118) 
- Pipeline Improvements [\#1117](https://github.com/PegaSysEng/pantheon/pull/1117) 
- Permissioning cli smart contract [\#1116](https://github.com/PegaSysEng/pantheon/pull/1116) 
- Adding default pending transactions value in PantheonControllerBuilder [\#1114](https://github.com/PegaSysEng/pantheon/pull/1114) 
- Fix intermittency in WorldStateDownloaderTest [\#1113](https://github.com/PegaSysEng/pantheon/pull/1113) 
- Reduce number of seen blocks and transactions Pantheon tracks [\#1112](https://github.com/PegaSysEng/pantheon/pull/1112) 
- Timeout long test [\#1111](https://github.com/PegaSysEng/pantheon/pull/1111) 
- Errorprone 2.3.3 upgrades [\#1110](https://github.com/PegaSysEng/pantheon/pull/1110) 
- Add metric to capture memory used by RocksDB table readers [\#1108](https://github.com/PegaSysEng/pantheon/pull/1108) 
- Don't allow creation of multiple gauges with the same name [\#1107](https://github.com/PegaSysEng/pantheon/pull/1107) 
- Update Peer Discovery to use NodePermissioningController [\#1105](https://github.com/PegaSysEng/pantheon/pull/1105) 
- Move starting world state download process inside WorldDownloadState [\#1104](https://github.com/PegaSysEng/pantheon/pull/1104) 
- Enable private Tx capability to Clique [\#1102](https://github.com/PegaSysEng/pantheon/pull/1102) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Enable private Tx capability to IBFT [\#1101](https://github.com/PegaSysEng/pantheon/pull/1101) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Version Upgrades [\#1100](https://github.com/PegaSysEng/pantheon/pull/1100) 
- Don't delete completed tasks from RocksDbTaskQueue [\#1099](https://github.com/PegaSysEng/pantheon/pull/1099) 
- Support flat mapping with multiple threads [\#1098](https://github.com/PegaSysEng/pantheon/pull/1098) 
- Add pipe stage name to thread while executing [\#1097](https://github.com/PegaSysEng/pantheon/pull/1097) 
- Use pipeline for world state download [\#1096](https://github.com/PegaSysEng/pantheon/pull/1096) 
- TXPool JSON RPC tweaks [\#1095](https://github.com/PegaSysEng/pantheon/pull/1095) 
- Add in-memory cache over world state download queue [\#1087](https://github.com/PegaSysEng/pantheon/pull/1087) 
- Trim default metrics [\#1086](https://github.com/PegaSysEng/pantheon/pull/1086) 
- Improve imported block log line [\#1085](https://github.com/PegaSysEng/pantheon/pull/1085) 
- Smart contract permission controller [\#1083](https://github.com/PegaSysEng/pantheon/pull/1083) 
- Add timeout when waiting for JSON-RPC, WebSocket RPC and Metrics services to stop [\#1082](https://github.com/PegaSysEng/pantheon/pull/1082) 
- Add pipeline framework to make parallel processing simpler [\#1077](https://github.com/PegaSysEng/pantheon/pull/1077) 
- Node permissioning controller [\#1075](https://github.com/PegaSysEng/pantheon/pull/1075) 
- Smart contract permission controller stub [\#1074](https://github.com/PegaSysEng/pantheon/pull/1074) 
- Expose a synchronous start method in Runner [\#1072](https://github.com/PegaSysEng/pantheon/pull/1072) 
- Changes in chain head should trigger new permissioning check for active peers [\#1071](https://github.com/PegaSysEng/pantheon/pull/1071) 
- Fix exceptions fetching metrics after world state download completes [\#1066](https://github.com/PegaSysEng/pantheon/pull/1066) 
- Accept transactions in the pool with nonce above account sender nonce [\#1065](https://github.com/PegaSysEng/pantheon/pull/1065) 
- Repair Istanbul to handle Eth/62 & Eth/63 [\#1063](https://github.com/PegaSysEng/pantheon/pull/1063) 
- Close Private Storage Provider [\#1059](https://github.com/PegaSysEng/pantheon/pull/1059) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Add labels to Pipelined tasks metrics [\#1057](https://github.com/PegaSysEng/pantheon/pull/1057) 
- Re-enable Quorum Synchronisation [\#1056](https://github.com/PegaSysEng/pantheon/pull/1056) 
- Don't log expected failures as errors [\#1054](https://github.com/PegaSysEng/pantheon/pull/1054) 
- Make findSuitablePeer abstract [\#1053](https://github.com/PegaSysEng/pantheon/pull/1053) 
- Track added at in txpool [\#1048](https://github.com/PegaSysEng/pantheon/pull/1048) 
- Fix ImportBlocksTask to only request from peers that claim to have the blocks [\#1047](https://github.com/PegaSysEng/pantheon/pull/1047) 
- Don't run the dao block validator if dao block is 0 [\#1044](https://github.com/PegaSysEng/pantheon/pull/1044) 
- Don't make unnecessary copies of data in RocksDbKeyValueStorage [\#1040](https://github.com/PegaSysEng/pantheon/pull/1040) 
- Update discovery logic to trust bootnodes only when out of sync [\#1039](https://github.com/PegaSysEng/pantheon/pull/1039) 
- Fix IndexOutOfBoundsException in DetermineCommonAncestorTask [\#1038](https://github.com/PegaSysEng/pantheon/pull/1038) 
- Add `rpc_modules` JSON-RPC [\#1036](https://github.com/PegaSysEng/pantheon/pull/1036) 
- Simple permissioning smart contract [\#1035](https://github.com/PegaSysEng/pantheon/pull/1035) 
- Refactor enodeurl to use inetaddr [\#1032](https://github.com/PegaSysEng/pantheon/pull/1032) 
- Update CLI options in mismatched genesis file message [\#1031](https://github.com/PegaSysEng/pantheon/pull/1031) 
- Remove dependence of eth.core on eth.permissioning [\#1030](https://github.com/PegaSysEng/pantheon/pull/1030) 
- Make alloc optional and provide nicer error messages when genesis config is invalid [\#1029](https://github.com/PegaSysEng/pantheon/pull/1029) 
- Handle metrics request closing before response is generated [\#1028](https://github.com/PegaSysEng/pantheon/pull/1028) 
- Change EthNetworkConfig bootnodes to always be URIs [\#1027](https://github.com/PegaSysEng/pantheon/pull/1027) 
- Avoid port conflicts in acceptance tests [\#1025](https://github.com/PegaSysEng/pantheon/pull/1025) 
- Include reference tests in jacoco [\#1024](https://github.com/PegaSysEng/pantheon/pull/1024) 
- Acceptance test - configurable gas price [\#1023](https://github.com/PegaSysEng/pantheon/pull/1023) 
- Get Internal logs and output [\#1022](https://github.com/PegaSysEng/pantheon/pull/1022) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Fix race condition in WebSocketService [\#1021](https://github.com/PegaSysEng/pantheon/pull/1021) 
- Ensure devp2p ports are written to ports file correctly [\#1020](https://github.com/PegaSysEng/pantheon/pull/1020) 
- Report the correct tcp port in PING packets when it differs from the UDP port [\#1019](https://github.com/PegaSysEng/pantheon/pull/1019) 
- Refactor transient transaction processor [\#1017](https://github.com/PegaSysEng/pantheon/pull/1017) 
- Resume world state download from existing queue [\#1016](https://github.com/PegaSysEng/pantheon/pull/1016) 
- IBFT Acceptance tests updated with longer timeout on first block [\#1015](https://github.com/PegaSysEng/pantheon/pull/1015) 
- Update IBFT acceptances tests to await first block [\#1013](https://github.com/PegaSysEng/pantheon/pull/1013) 
- Remove full hashimoto implementation as its never used [\#1011](https://github.com/PegaSysEng/pantheon/pull/1011) 
- Created SyncStatus notifications [\#1010](https://github.com/PegaSysEng/pantheon/pull/1010) 
- Address acceptance test intermittency [\#1008](https://github.com/PegaSysEng/pantheon/pull/1008) 
- Consider a world state download stalled after 100 requests with no progress [\#1007](https://github.com/PegaSysEng/pantheon/pull/1007) 
- Reduce log level when block miner is interrupted [\#1006](https://github.com/PegaSysEng/pantheon/pull/1006) 
- RunnerTest fail on Windows due to network startup timing issue [\#1005](https://github.com/PegaSysEng/pantheon/pull/1005) 
- Generate Private Contract Address [\#1004](https://github.com/PegaSysEng/pantheon/pull/1004) (thanks to [vinistevam](https://github.com/vinistevam))
- Delete the legacy pipelined import code [\#1003](https://github.com/PegaSysEng/pantheon/pull/1003) 
- Fix race condition in WebSocket AT [\#1002](https://github.com/PegaSysEng/pantheon/pull/1002) 
- Cleanup IBFT logging levels [\#995](https://github.com/PegaSysEng/pantheon/pull/995) 
- Integration Test implementation dependency for non-IntelliJ IDE [\#992](https://github.com/PegaSysEng/pantheon/pull/992) 
- Ignore fast sync and full sync tests to avoid race condition [\#991](https://github.com/PegaSysEng/pantheon/pull/991) 
- Make acceptance tests use the process based runner again [\#990](https://github.com/PegaSysEng/pantheon/pull/990) 
- RoundChangeCertificateValidator requires unique authors [\#989](https://github.com/PegaSysEng/pantheon/pull/989) 
- Make Rinkeby the benchmark chain.  [\#986](https://github.com/PegaSysEng/pantheon/pull/986) 
- Add metrics to Parallel Download pipeline [\#985](https://github.com/PegaSysEng/pantheon/pull/985) 
- Change ExpectBlockNumber to require at least the specified block number [\#981](https://github.com/PegaSysEng/pantheon/pull/981) 
- Fix benchmark compilation [\#980](https://github.com/PegaSysEng/pantheon/pull/980) 
- RPC tests can use 127.0.0.1 loopback rather than localhost [\#974](https://github.com/PegaSysEng/pantheon/pull/974) (fixes [\#956](https://github.com/PegaSysEng/pantheon/issues/956)[\#956](https://github.com/PegaSysEng/pantheon/issues/956) thanks to [glethuillier](https://github.com/glethuillier) for raising)
- Disable picocli ansi when testing [\#973](https://github.com/PegaSysEng/pantheon/pull/973) 
- Add a jmh benchmark for WorldStateDownloader [\#972](https://github.com/PegaSysEng/pantheon/pull/972) 
- Gradle dependency for JMH annotation, for IDEs that aren't IntelliJ \(… [\#971](https://github.com/PegaSysEng/pantheon/pull/971) 
- Separate download state tracking from WorldStateDownloader [\#967](https://github.com/PegaSysEng/pantheon/pull/967)
- Gradle dependency for JMH annotation, for IDEs that aren't IntelliJ [\#966](https://github.com/PegaSysEng/pantheon/pull/966) 
- Truffle HDwallet Web3 1.0 [\#964](https://github.com/PegaSysEng/pantheon/pull/964) 
- Add missing JavaDoc tags in JSONToRLP [\#963](https://github.com/PegaSysEng/pantheon/pull/963) 
- Only import block if it isn't already on the block chain [\#962](https://github.com/PegaSysEng/pantheon/pull/962) 
- CLI stack traces when debugging [\#960](https://github.com/PegaSysEng/pantheon/pull/960) 
- Create peer discovery packets on a worker thread [\#955](https://github.com/PegaSysEng/pantheon/pull/955) 
- Remove start functionality from IbftController and IbftBlockHeightMan… [\#952](https://github.com/PegaSysEng/pantheon/pull/952) 
- Cleanup IBFT executors [\#951](https://github.com/PegaSysEng/pantheon/pull/951) 
- Single threaded world state persistence [\#950](https://github.com/PegaSysEng/pantheon/pull/950) 
- Fix version number on master [\#946](https://github.com/PegaSysEng/pantheon/pull/946) 
- Change automatic benchmark  [\#945](https://github.com/PegaSysEng/pantheon/pull/945) 
- Eliminate redundant header validation [\#943](https://github.com/PegaSysEng/pantheon/pull/943) 
- RocksDbQueue Threading Tweaks [\#940](https://github.com/PegaSysEng/pantheon/pull/940) 
- Validate DAO block [\#939](https://github.com/PegaSysEng/pantheon/pull/939) 
- Complete Private Transaction Processor [\#938](https://github.com/PegaSysEng/pantheon/pull/938) (thanks to [iikirilov](https://github.com/iikirilov))
- Add metrics for netty queue length [\#932](https://github.com/PegaSysEng/pantheon/pull/932) 
- Update GetNodeDataFromPeerTask to return a map [\#931](https://github.com/PegaSysEng/pantheon/pull/931) 

## 1.0.1

Public key address export subcommand was missing in 1.0 release.

### Additions and Improvements
- Added `public-key export-address` subcommand [\#888](https://github.com/PegaSysEng/pantheon/pull/888)
- Documentation update for the [`public-key export-address`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#public-key) subcommand.
- Updated [IBFT 2.0 overview](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/IBFT/) to include use of `rlp encode` command and information on setting IBFT 2.0 properties to achieve your desired block time.

## 1.0 

### Additions and Improvements 
- [IBFT 2.0](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/IBFT/) 
- [Permissioning](https://docs.pantheon.pegasys.tech/en/latest/Permissions/Permissioning/) 
- [JSON-RPC Authentication](https://docs.pantheon.pegasys.tech/en/latest/JSON-RPC-API/Authentication/) 
- Added `rlp encode` subcommand [\#965](https://github.com/PegaSysEng/pantheon/pull/965)
- Method to reload permissions file [\#834](https://github.com/PegaSysEng/pantheon/pull/834) 
- Added rebind mitigation for Websockets. [\#905](https://github.com/PegaSysEng/pantheon/pull/905) 
- Support genesis contract code [\#749](https://github.com/PegaSysEng/pantheon/pull/749) (thanks to [kziemianek](https://github.com/kziemianek)). Fixes issue [\#662](https://github.com/PegaSysEng/pantheon/issues/662). 
- Documentation updates include: 
  - Added details on [port configuration](https://docs.pantheon.pegasys.tech/en/latest/Configuring-Pantheon/Networking/#port-configuration)    
  - Added [Resources page](https://docs.pantheon.pegasys.tech/en/latest/Resources/Resources/) linking to Pantheon blog posts and webinars 
  - Added [JSON-RPC Authentication](https://docs.pantheon.pegasys.tech/en/latest/JSON-RPC-API/Authentication/)  
  - Added [tutorial to create permissioned network](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-Permissioned-Network/) 
  - Added [Permissioning](https://docs.pantheon.pegasys.tech/en/latest/Permissions/Permissioning/) content 
  - Added [Permissioning API methods](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/#permissioning-methods) 
  - Added [tutorial to create Clique private network](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-Private-Clique-Network/)
  - Added [tutorial to create IBFT 2.0 private network](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-IBFT-Network/)
        
### Technical Improvements 
- RoundChangeCertificateValidator requires unique authors [\#997](https://github.com/PegaSysEng/pantheon/pull/997)
- RPC tests can use 127.0.0.1 loopback rather than localhost [\#979](https://github.com/PegaSysEng/pantheon/pull/979)
- Integration Test implementation dependency for non-IntelliJ IDE [\#978](https://github.com/PegaSysEng/pantheon/pull/978)
- Only import block if it isn't already on the block chain [\#977](https://github.com/PegaSysEng/pantheon/pull/977)
- Disable picocli ansi when testing [\#975](https://github.com/PegaSysEng/pantheon/pull/975)
- Create peer discovery packets on a worker thread [\#961](https://github.com/PegaSysEng/pantheon/pull/961)
- Removed Orion snapshot dependency [\#933](https://github.com/PegaSysEng/pantheon/pull/933) 
- Use network ID instead of chain ID in MainnetPantheonController. [\#929](https://github.com/PegaSysEng/pantheon/pull/929) 
- Propagate new block messages to other clients in a worker thread [\#928](https://github.com/PegaSysEng/pantheon/pull/928) 
- Parallel downloader should stop on puts if requested. [\#927](https://github.com/PegaSysEng/pantheon/pull/927) 
- Permission config file location and option under docker [\#925](https://github.com/PegaSysEng/pantheon/pull/925) 
- Fixed potential stall in world state download [\#922](https://github.com/PegaSysEng/pantheon/pull/922) 
- Refactoring to introduce deleteOnExit\(\) for temp files [\#920](https://github.com/PegaSysEng/pantheon/pull/920) 
- Reduce "Received transactions message" log from debug to trace [\#919](https://github.com/PegaSysEng/pantheon/pull/919) 
- Handle PeerNotConnected exceptions when sending wire keep alives [\#918](https://github.com/PegaSysEng/pantheon/pull/918) 
- admin_addpeers: error if node not whitelisted [\#917](https://github.com/PegaSysEng/pantheon/pull/917) 
- Expose the Ibft MiningCoordinator [\#916](https://github.com/PegaSysEng/pantheon/pull/916) 
- Check perm api against perm cli [\#915](https://github.com/PegaSysEng/pantheon/pull/915) 
- Update metrics when completing a world state request with existing data [\#914](https://github.com/PegaSysEng/pantheon/pull/914) 
- Improve RocksDBQueue dequeue performance [\#913](https://github.com/PegaSysEng/pantheon/pull/913) 
- Error when removing bootnodes from nodes whitelist [\#912](https://github.com/PegaSysEng/pantheon/pull/912) 
- Incremental Optimization\(s\) on BlockBroadcaster [\#911](https://github.com/PegaSysEng/pantheon/pull/911) 
- Check permissions CLI dependencies [\#909](https://github.com/PegaSysEng/pantheon/pull/909) 
- Limit the number of times we retry peer discovery interactions [\#908](https://github.com/PegaSysEng/pantheon/pull/908) 
- IBFT to use VoteTallyCache [\#907](https://github.com/PegaSysEng/pantheon/pull/907) 
- Add metric to expose number of inflight world state requests [\#906](https://github.com/PegaSysEng/pantheon/pull/906) 
- Bootnodes not on whitelist - improve errors [\#904](https://github.com/PegaSysEng/pantheon/pull/904) 
- Make chain download cancellable [\#901](https://github.com/PegaSysEng/pantheon/pull/901) 
- Enforce accounts must start with 0x [\#900](https://github.com/PegaSysEng/pantheon/pull/900) 
- When picking fast sync pivot block, use the peer with the best total difficulty [\#899](https://github.com/PegaSysEng/pantheon/pull/899) 
- Process world state download data on a worker thread [\#898](https://github.com/PegaSysEng/pantheon/pull/898) 
- CLI mixin help [\#895](https://github.com/PegaSysEng/pantheon/pull/895) ([macfarla](https://github.com/macfarla))
- Use absolute datapath instead of relative. [\#894](https://github.com/PegaSysEng/pantheon/pull/894). Fixes issue [\#854](https://github.com/PegaSysEng/pantheon/issues/854).
- Fix task queue so that the updated failure count for requests is stored [\#893](https://github.com/PegaSysEng/pantheon/pull/893) 
- Fix authentication header [\#891](https://github.com/PegaSysEng/pantheon/pull/891) 
- Reorganize eth tasks [\#890](https://github.com/PegaSysEng/pantheon/pull/890) 
- Unit tests of BlockBroadcaster [\#887](https://github.com/PegaSysEng/pantheon/pull/887) 
- Fix authentication file validation errors [\#886](https://github.com/PegaSysEng/pantheon/pull/886) 
- Fixing file locations under docker [\#885](https://github.com/PegaSysEng/pantheon/pull/885) 
- Handle exceptions properly in EthScheduler [\#884](https://github.com/PegaSysEng/pantheon/pull/884)
- More bootnodes for goerli [\#880](https://github.com/PegaSysEng/pantheon/pull/880)
- Rename password hash command [\#879](https://github.com/PegaSysEng/pantheon/pull/879) 
- Add metrics for EthScheduler executors [\#878](https://github.com/PegaSysEng/pantheon/pull/878) 
- Disconnect peer removed from node whitelist [\#877](https://github.com/PegaSysEng/pantheon/pull/877) 
- Reduce logging noise from invalid peer discovery packets and handshaking [\#876](https://github.com/PegaSysEng/pantheon/pull/876) 
- Detect stalled world state downloads [\#875](https://github.com/PegaSysEng/pantheon/pull/875) 
- Limit size of Ibft future message buffer [\#873](https://github.com/PegaSysEng/pantheon/pull/873) 
- Ibft2: Replace NewRound with extended Proposal [\#872](https://github.com/PegaSysEng/pantheon/pull/872) 
- Fixed admin_addPeer to periodically check maintained connections [\#871](https://github.com/PegaSysEng/pantheon/pull/871) 
- WebSocket method permissions [\#870](https://github.com/PegaSysEng/pantheon/pull/870)
- Select new pivot block when world state becomes unavailable [\#869](https://github.com/PegaSysEng/pantheon/pull/869) 
- Introduce FutureUtils to reduce duplicated code around CompletableFuture [\#868](https://github.com/PegaSysEng/pantheon/pull/868) 
- Implement world state cancel [\#867](https://github.com/PegaSysEng/pantheon/pull/867) 
- Renaming authentication configuration file CLI command [\#865](https://github.com/PegaSysEng/pantheon/pull/865) 
- Break out RoundChangeCertificate validation [\#864](https://github.com/PegaSysEng/pantheon/pull/864) 
- Disconnect peers where the common ancestor is before our fast sync pivot [\#862](https://github.com/PegaSysEng/pantheon/pull/862) 
- Initial scaffolding for block propagation [\#860](https://github.com/PegaSysEng/pantheon/pull/860) 
- Fix NullPointerException when determining fast sync pivot [\#859](https://github.com/PegaSysEng/pantheon/pull/859) 
- Check for invalid token [\#856](https://github.com/PegaSysEng/pantheon/pull/856) 
- Moving NodeWhitelistController to permissioning package [\#855](https://github.com/PegaSysEng/pantheon/pull/855) 
- Fix state download race condition by creating a TaskQueue API [\#853](https://github.com/PegaSysEng/pantheon/pull/853) 
- Changed separator in JSON RPC permissions [\#852](https://github.com/PegaSysEng/pantheon/pull/852) 
- WebSocket acceptance tests now can use WebSockets [\#851](https://github.com/PegaSysEng/pantheon/pull/851) 
- IBFT notifies EthPeer when remote node has a better block [\#849](https://github.com/PegaSysEng/pantheon/pull/849) 
- Support resuming fast-sync downloads [\#848](https://github.com/PegaSysEng/pantheon/pull/848) 
- Tweak Fast Sync Config [\#847](https://github.com/PegaSysEng/pantheon/pull/847) 
- RPC authentication configuration validation + tests. [\#846](https://github.com/PegaSysEng/pantheon/pull/846) 
- Tidy-up FastSyncState persistence [\#845](https://github.com/PegaSysEng/pantheon/pull/845) 
- Do parallel extract signatures in the parallel block importer. [\#844](https://github.com/PegaSysEng/pantheon/pull/844) 
- Fix 'the Input Is Too Long' Error on Windows [\#843](https://github.com/PegaSysEng/pantheon/pull/843) (thanks to [glethuillier](https://github.com/glethuillier)). Fixes issue [\#839](https://github.com/PegaSysEng/pantheon/issues/839). 
- Remove unnecessary sleep [\#842](https://github.com/PegaSysEng/pantheon/pull/842) 
- Shutdown improvements [\#841](https://github.com/PegaSysEng/pantheon/pull/841)
- Speed up shutdown time [\#838](https://github.com/PegaSysEng/pantheon/pull/838) 
- Add metrics to world state downloader [\#837](https://github.com/PegaSysEng/pantheon/pull/837) 
- Store pivot block header [\#836](https://github.com/PegaSysEng/pantheon/pull/836) 
- Clique should use beneficiary of zero on epoch blocks [\#833](https://github.com/PegaSysEng/pantheon/pull/833) 
- Clique should ignore proposals for address 0 [\#831](https://github.com/PegaSysEng/pantheon/pull/831) 
- Fix intermittency in FullSyncDownloaderTest [\#830](https://github.com/PegaSysEng/pantheon/pull/830) 
- Added the authentication service to the WebSocket service [\#829](https://github.com/PegaSysEng/pantheon/pull/829)
- Extract creation and init of ProtocolContext into a re-usable class [\#828](https://github.com/PegaSysEng/pantheon/pull/828) 
- Prevent duplicate commit seals in ibft header [\#827](https://github.com/PegaSysEng/pantheon/pull/827) 
- Validate Ibft vanity data length [\#826](https://github.com/PegaSysEng/pantheon/pull/826) 
- Refactored json rpc authentication to be provided as a service [\#825](https://github.com/PegaSysEng/pantheon/pull/825) 
- Handle unavailable world states [\#824](https://github.com/PegaSysEng/pantheon/pull/824) 
- Password in JWT payload [\#823](https://github.com/PegaSysEng/pantheon/pull/823)
- Homogenize error messages when required parameters are set [\#822](https://github.com/PegaSysEng/pantheon/pull/822) ([glethuillier](https://github.com/glethuillier)). Fixes issue [\#821](https://github.com/PegaSysEng/pantheon/issues/821). 
- Set remote peer chain head to parent of block received in NEW\_BLOCK\_MESSAGE [\#819](https://github.com/PegaSysEng/pantheon/pull/819) 
- Peer disconnects should not result in stack traces [\#818](https://github.com/PegaSysEng/pantheon/pull/818) 
- Abort previous builds [\#817](https://github.com/PegaSysEng/pantheon/pull/817) 
- Parallel build stages [\#816](https://github.com/PegaSysEng/pantheon/pull/816) 
- JWT authentication for JSON-RPC [\#815](https://github.com/PegaSysEng/pantheon/pull/815) 
- Log errors that occur while finding a common ancestor [\#814](https://github.com/PegaSysEng/pantheon/pull/814) 
- Shuffled log levels [\#813](https://github.com/PegaSysEng/pantheon/pull/813)
- Prevent duplicate IBFT messages being processed by state machine [\#811](https://github.com/PegaSysEng/pantheon/pull/811) 
- Fix Orion startup ports [\#810](https://github.com/PegaSysEng/pantheon/pull/810) 
- Commit world state continuously [\#809](https://github.com/PegaSysEng/pantheon/pull/809) 
- Improve block propagation time [\#808](https://github.com/PegaSysEng/pantheon/pull/808) 
- JSON-RPC authentication cli options & acceptance tests [\#807](https://github.com/PegaSysEng/pantheon/pull/807) 
- Remove privacy not supported warning [\#806](https://github.com/PegaSysEng/pantheon/pull/806) (thanks to [vinistevam](https://github.com/vinistevam))
- Wire up Private Transaction Processor [\#805](https://github.com/PegaSysEng/pantheon/pull/805) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Apply a limit to the number of responses in RespondingEthPeer.respondWhile [\#803](https://github.com/PegaSysEng/pantheon/pull/803) 
- Avoid requesting empty block bodies from the network. [\#802](https://github.com/PegaSysEng/pantheon/pull/802)
- Handle partial responses to get receipts requests [\#801](https://github.com/PegaSysEng/pantheon/pull/801) 
- Rename functions in Ibft MessageValidator [\#800](https://github.com/PegaSysEng/pantheon/pull/800) 
- Upgrade GoogleJavaFormat to 1.7 [\#795](https://github.com/PegaSysEng/pantheon/pull/795) 
- Minor refactorings of IntegrationTest infrastructure [\#786](https://github.com/PegaSysEng/pantheon/pull/786) 
- Rework Ibft MessageValidatorFactory [\#785](https://github.com/PegaSysEng/pantheon/pull/785) 
- Rework IbftRoundFactory [\#784](https://github.com/PegaSysEng/pantheon/pull/784) 
- Rename artefacts to artifacts within IBFT [\#782](https://github.com/PegaSysEng/pantheon/pull/782) 
- Rename TerminatedRoundArtefacts to PreparedRoundArtefacts [\#781](https://github.com/PegaSysEng/pantheon/pull/781)
- Rename Ibft MessageFactory methods [\#779](https://github.com/PegaSysEng/pantheon/pull/779) 
- Update WorldStateDownloader to only filter out known code requests [\#777](https://github.com/PegaSysEng/pantheon/pull/777) 
- Multiple name options only search for the longest one [\#776](https://github.com/PegaSysEng/pantheon/pull/776)
- Move ethTaskTimer to abstract root [\#775](https://github.com/PegaSysEng/pantheon/pull/775) 
- Parallel Block importer [\#774](https://github.com/PegaSysEng/pantheon/pull/774) 
- Wait for a peer with an estimated chain height before selecting a pivot block [\#772](https://github.com/PegaSysEng/pantheon/pull/772) 
- Randomly perform full validation when fast syncing blocks [\#770](https://github.com/PegaSysEng/pantheon/pull/770) 
- IBFT Message rework, piggybacking blocks on msgs. [\#769](https://github.com/PegaSysEng/pantheon/pull/769) 
- EthScheduler additions [\#767](https://github.com/PegaSysEng/pantheon/pull/767)
- Fixing node whitelist isPermitted check [\#766](https://github.com/PegaSysEng/pantheon/pull/766) 
- Eth/63 labels [\#764](https://github.com/PegaSysEng/pantheon/pull/764) 
- Permissioning whitelist persistence. [\#763](https://github.com/PegaSysEng/pantheon/pull/763) 
- Created message validators for NewRound and RoundChange [\#760](https://github.com/PegaSysEng/pantheon/pull/760) 
- Add tests for FastSyncChainDownloader as a whole [\#758](https://github.com/PegaSysEng/pantheon/pull/758) 
- Flatten IBFT Message API [\#757](https://github.com/PegaSysEng/pantheon/pull/757)
- Added TerminatedRoundArtefacts [\#756](https://github.com/PegaSysEng/pantheon/pull/756) 
- Fix thread names in EthScheduler to include the thread number [\#755](https://github.com/PegaSysEng/pantheon/pull/755) 
- Separate round change reception from RoundChangeCertificate [\#754](https://github.com/PegaSysEng/pantheon/pull/754) 
- JSON-RPC authentication login [\#753](https://github.com/PegaSysEng/pantheon/pull/753) 
- Spilt Ibft MessageValidator into components [\#752](https://github.com/PegaSysEng/pantheon/pull/752) 
- Ensure first checkpoint headers is always in local blockchain for FastSyncCheckpointHeaderManager [\#750](https://github.com/PegaSysEng/pantheon/pull/750) 
- Refactored permissioning components to be Optional. [\#747](https://github.com/PegaSysEng/pantheon/pull/747) 
- Integrate rocksdb-based queue into WorldStateDownloader [\#746](https://github.com/PegaSysEng/pantheon/pull/746) 
- Generify orion to enclave [\#745](https://github.com/PegaSysEng/pantheon/pull/745) (thanks to [vinistevam](https://github.com/vinistevam))
- Moved IBFT Message factory to use wrapped message types [\#744](https://github.com/PegaSysEng/pantheon/pull/744) 
- Handle timeouts when requesting checkpoint headers correctly [\#743](https://github.com/PegaSysEng/pantheon/pull/743)
- Update RoundChangeManager to use flattened message [\#742](https://github.com/PegaSysEng/pantheon/pull/742) 
- Handle validation failures when fast importing blocks [\#741](https://github.com/PegaSysEng/pantheon/pull/741) 
- Updated IbftRound and RoundState APIs to use wrapped messages [\#740](https://github.com/PegaSysEng/pantheon/pull/740)
- Exception handling [\#739](https://github.com/PegaSysEng/pantheon/pull/739)
- Upgrade dependency versions and build cleanup [\#738](https://github.com/PegaSysEng/pantheon/pull/738)
- Update IbftBlockHeigntManager to accept new message types. [\#737](https://github.com/PegaSysEng/pantheon/pull/737) 
- Error response handling for permissions APIs [\#736](https://github.com/PegaSysEng/pantheon/pull/736) 
- IPV6 bootnodes don't work [\#735](https://github.com/PegaSysEng/pantheon/pull/735) 
- Updated to use tags of pantheon build rather than another repo [\#734](https://github.com/PegaSysEng/pantheon/pull/734) 
- Log milestones at startup and other minor logging improvements [\#733](https://github.com/PegaSysEng/pantheon/pull/733) 
- Create wrapper types for Ibft Signed messages [\#731](https://github.com/PegaSysEng/pantheon/pull/731)
- Ibft to uniquely ID messages by their hash [\#730](https://github.com/PegaSysEng/pantheon/pull/730) 
- Rename ibftrevised to ibft2 [\#722](https://github.com/PegaSysEng/pantheon/pull/722)
- Limit ibft msg queues [\#704](https://github.com/PegaSysEng/pantheon/pull/704) 
- Implement privacy precompiled contract [\#696](https://github.com/PegaSysEng/pantheon/pull/696) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Integration of RecursivePeerRefreshState and PeerDiscoveryController [\#420](https://github.com/PegaSysEng/pantheon/pull/420) 

## 0.9.1

Built and compatible with with JDK8. 

## 0.9

### Breaking Changes to Command Line

Breaking changes have been made to the command line options in v0.9 to improve usability. Many v0.8 command line options no longer work. 

The [documentation](https://docs.pantheon.pegasys.tech/en/latest/) has been updated throughout to use the changed command line options and the [command line reference](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/) documents the changed options. 

| Previous Option                     | New Option                                                                                                                                                                                                                                  | Change                            |
|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------|
| `--config`                          | [`--config-file`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#config-file)                                                                                                                                  | Renamed                          |
| `--datadir`                         | [`--data-path`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#data-path)                                                                                                                                      | Renamed                          |
| `--dev-mode`                        | [`--network=dev`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#network)                                                                                                                                     | Replaced by `--network` option   |
| `--genesis`                         | [`--genesis-file`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#genesis-file)                                                                                                                                | Renamed                          |
| `--goerli`                          | [`--network=goerli`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#network)                                                                                                                                  | Replaced by `--network` option   |
| `--metrics-listen=<HOST:PORT>`      | [`--metrics-host=<HOST>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#metrics-host) and [`--metrics-port=<PORT>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#metrics-port) | Split into host and port options |
| `--miner-extraData`                 | [`--miner-extra-data`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#miner-extra-data)                                                                                                                       | Renamed                          |
| `--miner-minTransactionGasPriceWei` | [`--min-gas-price`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#min-gas-price)                                                                                                                              | Renamed                          |
| `--no-discovery`                    | [`--discovery-enabled`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#discovery-enabled)                                                                                                                      | Replaced                         |
| `--node-private-key`                | [`--node-private-key-file`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#node-private-key-file)                                                                                                              | Renamed                          |
| `--ottoman`                         | N/A                                                                                                                                                                                                                                         | Removed                          |
| `--p2p-listen=<HOST:PORT>`          | [`--p2p-host=<HOST>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#p2p-host) and [`--p2p-port=<PORT>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#p2p-port) | Split into host and port options |
| `--rinkeby`                         | [`--network=rinkeby`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#network)                                                                                                                                     | Replaced by `--network` option   |
| `--ropsten`                         | [`--network=ropsten`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#network)                                                                                                                                     | Replaced by `--network` option   |
| `--rpc-enabled`                     | [` --rpc-http-enabled`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-http-enabled)| Renamed| 
| `--rpc-listen=<HOST:PORT>`          | [`--rpc-http-host=<HOST>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-http-host) and [`--rpc-http-port=<PORT>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-http-port) | Split into host and port options |
| `--rpc-api`                         | [`--rpc-http-api`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-http-api)| Renamed |
| `--rpc-cors-origins`                | [`--rpc-http-cors-origins`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-http-cors-origins) | Renamed | 
| `--ws-enabled`                      | [`--rpc-ws-enabled`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-ws-enabled)  | Renamed | 
| `--ws-api`                          | [`--rpc-ws-api`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-ws-api) | Renamed|
| `--ws-listen=<HOST:PORT>`           | [`--rpc-ws-host=<HOST>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-ws-host) and [`--rpc-ws-port=<PORT>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-ws-port) | Split into host and port options |
| `--ws-refresh-delay`                | [`--rpc-ws-refresh-delay`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#rpc-ws-refresh-delay)|Renamed| 

| Previous Subcommand                 | New Subcommand                                                                                                                                                                                                                  | Change                            |
|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------|
| `pantheon import <block-file>`      | [`pantheon blocks import --from=<block-file>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#blocks)                                                                                            | Renamed                          |
| `pantheon export-pub-key <key-file>`| [`pantheon public-key export --to=<key-file>`](https://docs.pantheon.pegasys.tech/en/latest/Reference/Pantheon-CLI-Syntax/#public-key)                                                                                                      | Renamed                          |


### Private Network Quickstart 

The Private Network Quickstart has been moved from the `pantheon` repository to the `pantheon-quickstart` 
repository. The [Private Network Quickstart tutorial](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Private-Network-Quickstart/) 
has been updated to use the moved quickstart.

### Additions and Improvements 

- `--network=goerli` supports relaunch of Görli testnet [\#717](https://github.com/PegaSysEng/pantheon/pull/717)
- TOML authentication provider [\#689](https://github.com/PegaSysEng/pantheon/pull/689) 
- Metrics Push Gateway Options [\#678](https://github.com/PegaSysEng/pantheon/pull/678)
- Additional logging details for IBFT 2.0 [\#650](https://github.com/PegaSysEng/pantheon/pull/650) 
- Permissioning config TOML file [\#643](https://github.com/PegaSysEng/pantheon/pull/643)
- Added metrics Prometheus Push Gateway Support [\#638](https://github.com/PegaSysEng/pantheon/pull/638) 
- Clique and IBFT not enabled by default in RPC APIs [\#635](https://github.com/PegaSysEng/pantheon/pull/635)
- Added `admin_addPeer` JSON-RPC API method [\#622](https://github.com/PegaSysEng/pantheon/pull/622)
- Implemented `--p2p-enabled` configuration item [\#619](https://github.com/PegaSysEng/pantheon/pull/619) 
- Command options and commands renaming [\#618](https://github.com/PegaSysEng/pantheon/pull/618) 
- Added IBFT get pending votes [\#603](https://github.com/PegaSysEng/pantheon/pull/603) 
- Implement Petersburg hardfork [\#601](https://github.com/PegaSysEng/pantheon/pull/601) 
- Added private transaction abstraction [\#592](https://github.com/PegaSysEng/pantheon/pull/592) (thanks to [iikirilov](https://github.com/iikirilov))
- Added privacy command line commands [\#584](https://github.com/PegaSysEng/pantheon/pull/584) (thanks to [Puneetha17](https://github.com/Puneetha17))
- Documentation updates include:
  - Updated [Private Network Quickstart tutorial](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Private-Network-Quickstart/) 
    to use quickstart in `pantheon-quickstart` repository and indicate that the quickstart is not supported on Windows.
  - Added IBFT 2.0 [content](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/IBFT/) and [JSON RPC API methods](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/#ibft-20-methods). 
  - Added [consensus protocols content](https://docs.pantheon.pegasys.tech/en/latest/Consensus-Protocols/Comparing-PoA/).
  - Added content on [events and logs](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/Events-and-Logs/), and [using filters](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/Accessing-Logs-Using-JSON-RPC/). 
  - Added content on integrating with [Prometheus Push Gateway](https://docs.pantheon.pegasys.tech/en/latest/Using-Pantheon/Debugging/#running-prometheus-with-pantheon-in-push-mode)
  
### Technical Improvements 

- Download receipts during fast sync and import without processing transactions [\#701](https://github.com/PegaSysEng/pantheon/pull/701) 
- Removed CLI options for `--nodes-whitelist` and `--accounts-whitelist` [\#694](https://github.com/PegaSysEng/pantheon/pull/694) 
- Delegate `getRootCause` through to Guava's implementation [\#692](https://github.com/PegaSysEng/pantheon/pull/692) 
- Benchmark update [\#691](https://github.com/PegaSysEng/pantheon/pull/691)
- Implement chain download for fast sync [\#690](https://github.com/PegaSysEng/pantheon/pull/690) 
- Allow missing accounts to create zero-cost transactions [\#685](https://github.com/PegaSysEng/pantheon/pull/685) 
- Node private key location should be fixed under docker [\#684](https://github.com/PegaSysEng/pantheon/pull/684) 
- Parallel Processing File Import Performance [\#683](https://github.com/PegaSysEng/pantheon/pull/683) 
- Integrate actual `WorldStateDownloader` with the fast sync work flow [\#682](https://github.com/PegaSysEng/pantheon/pull/682) 
- Removed `--max-trailing-peers` option [\#680](https://github.com/PegaSysEng/pantheon/pull/680)
- Enabled warning on CLI dependent options [\#679](https://github.com/PegaSysEng/pantheon/pull/679)
- Update WorldStateDownloader run\(\) interface to accept header [\#677](https://github.com/PegaSysEng/pantheon/pull/677)
- Fixed Difficulty calculator [\#663](https://github.com/PegaSysEng/pantheon/pull/663)
- `discovery-enabled` option refactoring [\#661](https://github.com/PegaSysEng/pantheon/pull/661)
- Update orion default port approach [\#660](https://github.com/PegaSysEng/pantheon/pull/660)
- Extract out generic parts of Downloader [\#659](https://github.com/PegaSysEng/pantheon/pull/659)
- Start world downloader [\#658](https://github.com/PegaSysEng/pantheon/pull/658)
- Create a simple `WorldStateDownloader` [\#657](https://github.com/PegaSysEng/pantheon/pull/657) 
- Added handling for when p2p is disabled [\#655](https://github.com/PegaSysEng/pantheon/pull/655)
- Enabled command line configuration for privacy precompiled contract address [\#653](https://github.com/PegaSysEng/pantheon/pull/653) (thanks to [Puneetha17](https://github.com/Puneetha17))
- IBFT transmitted packets are logged by gossiper [\#652](https://github.com/PegaSysEng/pantheon/pull/652)
- `admin_addPeer` acceptance test [\#651](https://github.com/PegaSysEng/pantheon/pull/651) 
- Added `p2pEnabled` configuration to `ProcessPantheonNodeRunner` [\#649](https://github.com/PegaSysEng/pantheon/pull/649) 
- Added description to automatic benchmarks [\#646](https://github.com/PegaSysEng/pantheon/pull/646) 
- Added `network` option [\#645](https://github.com/PegaSysEng/pantheon/pull/645) 
- Remove OrionConfiguration [\#644](https://github.com/PegaSysEng/pantheon/pull/644) (thanks to [Puneetha17](https://github.com/Puneetha17))
- IBFT Json Acceptance tests [\#634](https://github.com/PegaSysEng/pantheon/pull/634)
- Upgraded build image to one that contains libsodium [\#632](https://github.com/PegaSysEng/pantheon/pull/632) 
- Command line fixes [\#630](https://github.com/PegaSysEng/pantheon/pull/630) 
- Consider peer count insufficient until minimum peers for fast sync are connected [\#629](https://github.com/PegaSysEng/pantheon/pull/629) 
- Build tweaks [\#628](https://github.com/PegaSysEng/pantheon/pull/628)
- IBFT ensure non-validator does not partake in consensus [\#627](https://github.com/PegaSysEng/pantheon/pull/627)
- Added ability in acceptance tests to set up a node with `--no-discovery` [\#624](https://github.com/PegaSysEng/pantheon/pull/624)
- Gossip integration test [\#623](https://github.com/PegaSysEng/pantheon/pull/623)
- Removed quickstart code and CI pipeline [\#616](https://github.com/PegaSysEng/pantheon/pull/616) 
- IBFT Integration Tests - Spurious Behaviour [\#615](https://github.com/PegaSysEng/pantheon/pull/615) 
- Refactoring for more readable IBFT IT [\#614](https://github.com/PegaSysEng/pantheon/pull/614) 
- Start of fast sync downloader [\#613](https://github.com/PegaSysEng/pantheon/pull/613) 
- Split `IbftProcessor` into looping and event processing [\#612](https://github.com/PegaSysEng/pantheon/pull/612) 
- IBFT Int Test - changed `TestContextFactory` to a builder [\#611](https://github.com/PegaSysEng/pantheon/pull/611) 
- Discard prior round change msgs [\#610](https://github.com/PegaSysEng/pantheon/pull/610) 
- `IbftGetValidatorsByBlockHash` added to json factory [\#607](https://github.com/PegaSysEng/pantheon/pull/607) 
- IBFT Validator RPCs to return list of strings [\#606](https://github.com/PegaSysEng/pantheon/pull/606) 
- Update Benchmark [\#605](https://github.com/PegaSysEng/pantheon/pull/605) 
- Remove db package and move classes to more appropriate locations [\#599](https://github.com/PegaSysEng/pantheon/pull/599) 
- Added `GetReceiptsFromPeerTask` [\#598](https://github.com/PegaSysEng/pantheon/pull/598) 
- Added `GetNodeDataFromPeerTask` [\#597](https://github.com/PegaSysEng/pantheon/pull/597) 
- Fixed deprecation warnings [\#596](https://github.com/PegaSysEng/pantheon/pull/596) 
- IBFT Integration Tests - Future Height [\#591](https://github.com/PegaSysEng/pantheon/pull/591) 
- Added `getNodeData` to `EthPeer` to enable requesting node data [\#589](https://github.com/PegaSysEng/pantheon/pull/589) 
- `Blockcreator` to use `parentblock` specified at constuction [\#588](https://github.com/PegaSysEng/pantheon/pull/588) 
- Support responding to `GetNodeData` requests [\#587](https://github.com/PegaSysEng/pantheon/pull/587) 
- IBFT validates block on proposal reception [\#583](https://github.com/PegaSysEng/pantheon/pull/583) 
- Rework `NewRoundValidator` tests [\#582](https://github.com/PegaSysEng/pantheon/pull/582) 
- IBFT split extra data validation rule into components [\#581](https://github.com/PegaSysEng/pantheon/pull/581) 
- Allow attached rules to be flagged `light` [\#580](https://github.com/PegaSysEng/pantheon/pull/580) 
- Split Block Validation from Importing [\#579](https://github.com/PegaSysEng/pantheon/pull/579) 
- Refactor `RoundChangeManager` creation [\#578](https://github.com/PegaSysEng/pantheon/pull/578) 
- Add `-SNAPSHOT` postfix to version [\#577](https://github.com/PegaSysEng/pantheon/pull/577) 
- IBFT - prevent proposed block being imported twice [\#576](https://github.com/PegaSysEng/pantheon/pull/576) 
- Version upgrades [\#571](https://github.com/PegaSysEng/pantheon/pull/571) 
- Tests that CLI options are disabled under docker [\#566](https://github.com/PegaSysEng/pantheon/pull/566) 
- Renamed IBFT networking classes [\#555](https://github.com/PegaSysEng/pantheon/pull/555) 
- Removed dead code from the consensus package [\#554](https://github.com/PegaSysEng/pantheon/pull/554) 
- Prepared private transaction support [\#538](https://github.com/PegaSysEng/pantheon/pull/538) (thanks to [iikirilov](https://github.com/iikirilov))

## 0.8.5

Indefinitely delays the roll-out of Constantinople on Ethereum Mainnet due to a [potential security issue](https://blog.ethereum.org/2019/01/15/security-alert-ethereum-constantinople-postponement/) detected.

## Additions and Improvements
- Remove Constantinople fork block [\#574](https://github.com/PegaSysEng/pantheon/pull/574)

## Technical Improvements
- Rename IBFT message packages [\#568](https://github.com/PegaSysEng/pantheon/pull/568)


## 0.8.4

### Docker Image

If you have been running a node using the v0.8.3 Docker image, the node was not saving data to the 
specified [data directory](https://docs.pantheon.pegasys.tech/en/latest/Getting-Started/Run-Docker-Image/#data-directory),
or referring to the custom [configuration file](https://docs.pantheon.pegasys.tech/en/latest/Getting-Started/Run-Docker-Image/#custom-configuration-file)
or [genesis file](https://docs.pantheon.pegasys.tech/en/latest/Getting-Started/Run-Docker-Image/#custom-genesis-file). 

To recover the node key and data directory from the Docker container:
`docker cp <container>:/opt/pantheon/key <destination_file>`
`docker cp <container>:/opt/pantheon/database <destination_directory>` 

Where `container` is the name or ID of the Docker container containing the Pantheon node. 

The container can be running or stopped when you copy the key and data directory. If your node was 
fully synchronized to MainNet, the data directory will be ~2TB.  

When restarting your node with the v0.8.4 Docker image:

* Save the node key in the [`key` file](https://docs.pantheon.pegasys.tech/en/latest/Configuring-Pantheon/Node-Keys/#node-private-key) in the data 
    directory or specify the location using the [`--node-private-key` option](https://docs.pantheon.pegasys.tech/en/latest/Configuring-Pantheon/Node-Keys/#specifying-a-custom-node-private-key-file).  
* Specify the `<destination_directory` as a [volume for the data directory](https://docs.pantheon.pegasys.tech/en/latest/Getting-Started/Run-Docker-Image/#data-directory). 

### Bug Fixes
- Fixing default resource locations inside docker [\#529](https://github.com/PegaSysEng/pantheon/pull/529)
- NewRoundMessageValidator ignores Round Number when comparing blocks [\#523](https://github.com/PegaSysEng/pantheon/pull/523) 
- Fix Array Configurable command line options [\#514](https://github.com/PegaSysEng/pantheon/pull/514) 

## Additions and Improvements
- RocksDB Metrics [\#531](https://github.com/PegaSysEng/pantheon/pull/531) 
- Added `ibft_getValidatorsByBlockHash` JSON RPC [\#519](https://github.com/PegaSysEng/pantheon/pull/519) 
- Expose metrics to Prometheus [\#506](https://github.com/PegaSysEng/pantheon/pull/506)
- Added `ibft_getValidatorsByBlockNumber` [\#499](https://github.com/PegaSysEng/pantheon/pull/499) 
- Added `Roadmap.md` file. [\#494](https://github.com/PegaSysEng/pantheon/pull/494) 
- Added JSON RPC `eth hashrate` method. [\#488](https://github.com/PegaSysEng/pantheon/pull/488) 
- Account whitelist API [\#487](https://github.com/PegaSysEng/pantheon/pull/487) 
- Added nodes whitelist JSON-RPC APIs [\#476](https://github.com/PegaSysEng/pantheon/pull/476) 
- Added account whitelisting [\#460](https://github.com/PegaSysEng/pantheon/pull/460) 
- Added configurable refresh delay for SyncingSubscriptionService on start up [\#383](https://github.com/PegaSysEng/pantheon/pull/383) 
- Added the Command Line Style Guide  [\#530](https://github.com/PegaSysEng/pantheon/pull/530)
- Documentation updates include:
  * Migrated to new [documentation site](https://docs.pantheon.pegasys.tech/en/latest/)  
  * Added [configuration file content](https://docs.pantheon.pegasys.tech/en/latest/Configuring-Pantheon/Using-Configuration-File/)
  * Added [tutorial to create private network](https://docs.pantheon.pegasys.tech/en/latest/Tutorials/Create-Private-Network/)
  * Added content on [enabling non-default APIs](https://docs.pantheon.pegasys.tech/en/latest/Reference/JSON-RPC-API-Methods/)
  
## Technical Improvements

-  Updated `--bootnodes` command option to take zero arguments [\#548](https://github.com/PegaSysEng/pantheon/pull/548) 
- IBFT Integration Testing - Local Node is proposer [\#527](https://github.com/PegaSysEng/pantheon/pull/527) 
- Remove vertx from discovery tests [\#539](https://github.com/PegaSysEng/pantheon/pull/539)
- IBFT Integration testing - Round Change [\#537](https://github.com/PegaSysEng/pantheon/pull/537) 
- NewRoundMessageValidator creates RoundChangeValidator with correct value [\#518](https://github.com/PegaSysEng/pantheon/pull/518) 
- Remove time dependency from BlockTimer tests [\#513](https://github.com/PegaSysEng/pantheon/pull/513) 
- Gradle 5.1 [\#512](https://github.com/PegaSysEng/pantheon/pull/512) 
- Metrics measurement adjustment [\#511](https://github.com/PegaSysEng/pantheon/pull/511) 
- Metrics export for import command. [\#509](https://github.com/PegaSysEng/pantheon/pull/509) 
- IBFT Integration test framework [\#502](https://github.com/PegaSysEng/pantheon/pull/502) 
- IBFT message gossiping [\#501](https://github.com/PegaSysEng/pantheon/pull/501) 
- Remove non-transactional mutation from KeyValueStore [\#500](https://github.com/PegaSysEng/pantheon/pull/500) 
- Ensured that the blockchain queries class handles optionals better. [\#486](https://github.com/PegaSysEng/pantheon/pull/486) 
- IBFT mining acceptance test [\#483](https://github.com/PegaSysEng/pantheon/pull/483) 
- Set base directory name to be lowercase in building.md [\#474](https://github.com/PegaSysEng/pantheon/pull/474) (Thanks to [Matthalp](https://github.com/Matthalp))
- Moved admin\_peers to Admin API group [\#473](https://github.com/PegaSysEng/pantheon/pull/473) 
- Nodes whitelist acceptance test [\#472](https://github.com/PegaSysEng/pantheon/pull/472) 
- Rework RoundChangeManagerTest to not reuse validators [\#469](https://github.com/PegaSysEng/pantheon/pull/469) 
- Ignore node files to support truffle. [\#467](https://github.com/PegaSysEng/pantheon/pull/467) 
- IBFT pantheon controller [\#461](https://github.com/PegaSysEng/pantheon/pull/461) 
- IBFT Round to update internal state on reception of NewRound Message [\#451](https://github.com/PegaSysEng/pantheon/pull/451) 
- Update RoundChangeManager correctly create its message validator [\#450](https://github.com/PegaSysEng/pantheon/pull/450) 
- Use seconds for block timer time unit [\#445](https://github.com/PegaSysEng/pantheon/pull/445) 
- IBFT controller and future msgs handling [\#431](https://github.com/PegaSysEng/pantheon/pull/431) 
- Allow IBFT Round to be created using PreparedCert [\#429](https://github.com/PegaSysEng/pantheon/pull/429) 
- Added MessageValidatorFactory [\#425](https://github.com/PegaSysEng/pantheon/pull/425) 
- Inround payload [\#423](https://github.com/PegaSysEng/pantheon/pull/423) 
- Updated IbftConfig Fields [\#422](https://github.com/PegaSysEng/pantheon/pull/422) 
- Repair IbftBlockCreator and add tests [\#421](https://github.com/PegaSysEng/pantheon/pull/421) 
- Make Pantheon behave as a submodule [\#419](https://github.com/PegaSysEng/pantheon/pull/419) 
- Ibft Height Manager [\#418](https://github.com/PegaSysEng/pantheon/pull/418) 
- Ensure bootnodes are a subset of node whitelist [\#414](https://github.com/PegaSysEng/pantheon/pull/414) 
- IBFT Consensus Round Classes [\#405](https://github.com/PegaSysEng/pantheon/pull/405) 
- IBFT message payload tests [\#404](https://github.com/PegaSysEng/pantheon/pull/404) 
- Validate enodeurl syntax from command line [\#403](https://github.com/PegaSysEng/pantheon/pull/403) 
- Update errorprone [\#401](https://github.com/PegaSysEng/pantheon/pull/401) 
- IBFT round change manager [\#393](https://github.com/PegaSysEng/pantheon/pull/393) 
- IBFT RoundState [\#392](https://github.com/PegaSysEng/pantheon/pull/392) 
- Move Block data generator test helper to test support package [\#391](https://github.com/PegaSysEng/pantheon/pull/391) 
- IBFT message tests [\#367](https://github.com/PegaSysEng/pantheon/pull/367) 

## 0.8.3 

### Breaking Change to JSON RPC-API 

From v0.8.3, incoming HTTP requests are only accepted from hostnames specified using the `--host-whitelist` command-line option. If not specified, the default value for `--host-whitelist` is `localhost`. 

If using the URL `http://127.0.0.1` to make JSON-RPC calls, use `--host-whitelist` to specify the hostname `127.0.0.1` or update the hostname to `localhost`. 

If your application publishes RPC ports, specify the hostnames when starting Pantheon. For example:  

```json
pantheon --host-whitelist=example.com
```
 
Specify `*` or `all` for `--host-whitelist` to effectively disable host protection and replicate pre-v0.8.3 behavior. This is not recommended for production code. 

### Bug Fixes 

- Repair Clique Proposer Selection [\#339](https://github.com/PegaSysEng/pantheon/pull/339) 
- High TX volume swamps block processing [\#337](https://github.com/PegaSysEng/pantheon/pull/337) 
- Check if the connectFuture has completed successfully [\#293](https://github.com/PegaSysEng/pantheon/pull/293) 
- Switch back to Xerial Snappy Library [\#284](https://github.com/PegaSysEng/pantheon/pull/284) 
- ShortHex of 0 should be '0x0', not '0x' [\#272](https://github.com/PegaSysEng/pantheon/pull/272) 
- Fix pantheon CLI default values infinite loop [\#266](https://github.com/PegaSysEng/pantheon/pull/266) 

### Additions and Improvements 

- Added `--nodes-whitelist` parameter to CLI and NodeWhitelistController [\#346](https://github.com/PegaSysEng/pantheon/pull/346) 
- Discovery wiring for `--node-whitelist` [\#365](https://github.com/PegaSysEng/pantheon/pull/365) 
- Plumb in three more metrics [\#344](https://github.com/PegaSysEng/pantheon/pull/344) 
- `ProposerSelection` to support multiple IBFT implementations [\#307](https://github.com/PegaSysEng/pantheon/pull/307) 
- Configuration to support IBFT original and revised [\#306](https://github.com/PegaSysEng/pantheon/pull/306) 
- Added host whitelist for JSON-RPC. [**Breaking Change**](#breaking-change-to-json-rpc-api) [\#295](https://github.com/PegaSysEng/pantheon/pull/295) 
- Reduce `Block creation processed cancelled` log message to debug [\#294](https://github.com/PegaSysEng/pantheon/pull/294) 
- Implement iterative peer search [\#268](https://github.com/PegaSysEng/pantheon/pull/268) 
- Added RLP enc/dec for PrePrepare, Commit and NewRound messages [\#200](https://github.com/PegaSysEng/pantheon/pull/200) 
- IBFT block mining [\#169](https://github.com/PegaSysEng/pantheon/pull/169) 
- Added `--goerli` CLI option [\#370](https://github.com/PegaSysEng/pantheon/pull/370) (Thanks to [@Nashatyrev](https://github.com/Nashatyrev))
- Begin capturing metrics to better understand Pantheon's behaviour [\#326](https://github.com/PegaSysEng/pantheon/pull/326)
- Documentation updates include:
   * Added Coding Conventions [\#342](https://github.com/PegaSysEng/pantheon/pull/342)
   * Reorganised [Installation documentation](https://github.com/PegaSysEng/pantheon/wiki/Installation) and added [Chocolatey installation](https://github.com/PegaSysEng/pantheon/wiki/Install-Binaries#windows-with-chocolatey) for Windows 
   * Reorganised [JSON-RPC API documentation](https://github.com/PegaSysEng/pantheon/wiki/JSON-RPC-API)
   * Updated [RPC Pub/Sub API documentation](https://github.com/PegaSysEng/pantheon/wiki/RPC-PubSub)
   
### Technical Improvements 

- Extracted non-Docker CLI parameters to picoCLI mixin. [\#323](https://github.com/PegaSysEng/pantheon/pull/323) 
- IBFT preprepare to validate round matches block [\#329](https://github.com/PegaSysEng/pantheon/pull/329) 
- Fix acceptance test [\#324](https://github.com/PegaSysEng/pantheon/pull/324) 
- Added the `IbftFinalState` [\#385](https://github.com/PegaSysEng/pantheon/pull/385) 
- Constantinople Fork Block [\#382](https://github.com/PegaSysEng/pantheon/pull/382) 
- Fix `pantheon.cli.PantheonCommandTest` test on Windows [\#380](https://github.com/PegaSysEng/pantheon/pull/380) 
- JDK smoke testing is being configured differently now [\#374](https://github.com/PegaSysEng/pantheon/pull/374) 
- Re-enable clique AT [\#373](https://github.com/PegaSysEng/pantheon/pull/373) 
- Ignoring acceptance test [\#372](https://github.com/PegaSysEng/pantheon/pull/372) 
- Changes to support Gradle 5.0 [\#371](https://github.com/PegaSysEng/pantheon/pull/371) 
- Clique: Prevent out of turn blocks interrupt in-turn mining [\#364](https://github.com/PegaSysEng/pantheon/pull/364) 
- Time all tasks [\#361](https://github.com/PegaSysEng/pantheon/pull/361) 
- Rework `VoteTallyCache` to better represent purpose [\#360](https://github.com/PegaSysEng/pantheon/pull/360) 
- Add an `UNKNOWN` `DisconnectReason` [\#359](https://github.com/PegaSysEng/pantheon/pull/359) 
- New round validation [\#353](https://github.com/PegaSysEng/pantheon/pull/353) 
- Update get validators for block hash test to start from block 1 [\#352](https://github.com/PegaSysEng/pantheon/pull/352) 
- Idiomatic Builder Pattern [\#345](https://github.com/PegaSysEng/pantheon/pull/345) 
- Revert `Repair Clique Proposer Selection` \#339 - Breaks Görli testnet [\#343](https://github.com/PegaSysEng/pantheon/pull/343) 
- No fixed ports in tests [\#340](https://github.com/PegaSysEng/pantheon/pull/340) 
- Update clique acceptance test genesis file to use correct clique property names [\#338](https://github.com/PegaSysEng/pantheon/pull/338) 
- Supporting list of addresses in logs subscription [\#336](https://github.com/PegaSysEng/pantheon/pull/336) 
- Render handler exception to `System.err` instead of `.out` [\#334](https://github.com/PegaSysEng/pantheon/pull/334) 
- Renamed IBFT message classes [\#333](https://github.com/PegaSysEng/pantheon/pull/333) 
- Add additional RLP tests [\#332](https://github.com/PegaSysEng/pantheon/pull/332) 
- Downgrading spotless to 3.13.0 to fix threading issues [\#325](https://github.com/PegaSysEng/pantheon/pull/325) 
- `eth_getTransactionReceipt` acceptance test [\#322](https://github.com/PegaSysEng/pantheon/pull/322) 
- Upgrade vertx to 3.5.4 [\#316](https://github.com/PegaSysEng/pantheon/pull/316) 
- Round change validation [\#315](https://github.com/PegaSysEng/pantheon/pull/315) 
- Basic IBFT message validators [\#314](https://github.com/PegaSysEng/pantheon/pull/314) 
- Minor repairs to clique block scheduling [\#308](https://github.com/PegaSysEng/pantheon/pull/308) 
- Dependencies Version upgrade [\#303](https://github.com/PegaSysEng/pantheon/pull/303)
- Build multiple JVM [\#301](https://github.com/PegaSysEng/pantheon/pull/301) 
- Smart contract acceptance test [\#296](https://github.com/PegaSysEng/pantheon/pull/296) 
- Fixing WebSocket error response [\#292](https://github.com/PegaSysEng/pantheon/pull/292) 
- Reword error messages following exceptions during mining [\#291](https://github.com/PegaSysEng/pantheon/pull/291) 
- Clique acceptance tests [\#290](https://github.com/PegaSysEng/pantheon/pull/290) 
- Delegate creation of additional JSON-RPC methods to the PantheonController [\#289](https://github.com/PegaSysEng/pantheon/pull/289) 
- Remove unnecessary `RlpInput` and `RlpOutput` classes [\#287](https://github.com/PegaSysEng/pantheon/pull/287) 
- Remove `RlpUtils` [\#285](https://github.com/PegaSysEng/pantheon/pull/285) 
- Enabling previously ignored acceptance tests [\#282](https://github.com/PegaSysEng/pantheon/pull/282) 
- IPv6 peers [\#281](https://github.com/PegaSysEng/pantheon/pull/281) 
- IPv6 Bootnode [\#280](https://github.com/PegaSysEng/pantheon/pull/280) 
- Acceptance test for `getTransactionReceipt` JSON-RPC method [\#278](https://github.com/PegaSysEng/pantheon/pull/278)
- Inject `StorageProvider` into `PantheonController` instances [\#259](https://github.com/PegaSysEng/pantheon/pull/259)

## 0.8.2
 
### Removed
 - Removed `import-blockchain` command because nothing exports to the required format yet (PR [\#223](https://github.com/PegaSysEng/pantheon/pull/223))

### Bug Fixes
 - `io.netty.util.internal.OutOfDirectMemoryError` errors by removing reference counting from network messages.
 - Log spam: endless loop in `nioEventLoopGroup` ([#248](https://github.com/PegaSysEng/pantheon/issues/248) thanks to [@5chdn](https://github.com/5chdn) for reporting) (PR [#261](https://github.com/PegaSysEng/pantheon/pull/261))
 - Rinkeby import can stall with too many fragments ([#228](https://github.com/PegaSysEng/pantheon/issues/228) thanks to [@steffenkux](https://github.com/steffenkux) and [@5chdn](https://github.com/5chdn) for reporting) (PR [#255](https://github.com/PegaSysEng/pantheon/pull/255))
 - Clique incorrectly used the chain ID instead of the network ID in ETH status messages (PR [#209](https://github.com/PegaSysEng/pantheon/pull/209))
 - Gradle deprecation warnings (PR [#246](https://github.com/PegaSysEng/pantheon/pull/246) with thanks to [@jvirtanen](https://github.com/jvirtanen))
 - Consensus issue on Ropsten:
    - Treat output length as a maximum length for CALL operations (PR [#236](https://github.com/PegaSysEng/pantheon/pull/236))
    - ECRec precompile should return empty instead of 32 zero bytes when the input is invalid (PR [#227](https://github.com/PegaSysEng/pantheon/pull/227))
 - File name too long error while building from source ([#215](https://github.com/PegaSysEng/pantheon/issues/215) thanks to [@5chdn](https://github.com/5chdn) for reporting) (PR [#221](https://github.com/PegaSysEng/pantheon/pull/221))
 - Loop syntax in `runPantheonPrivateNetwork.sh` (PR [#237](https://github.com/PegaSysEng/pantheon/pull/237) thanks to [@matt9ucci](https://github.com/matt9ucci))
 - Fix `CompressionException: Snappy decompression failed` errors ([#251](https://github.com/PegaSysEng/pantheon/issues/251) thanks to [@5chdn](https://github.com/5chdn) for reporting) (PR [#274](https://github.com/PegaSysEng/pantheon/pull/274))

### Additions and Improvements
 - Added `--ropsten` command line argument to make syncing to Ropsten easier ([#186](https://github.com/PegaSysEng/pantheon/issues/186)) (PR [#197](https://github.com/PegaSysEng/pantheon/pull/197) with thanks to [@jvirtanen](https://github.com/jvirtanen))
 - Enabled constantinople in `--dev-mode` (PR [#256](https://github.com/PegaSysEng/pantheon/pull/256))
 - Supported Constantinople with Clique ([#245](https://github.com/PegaSysEng/pantheon/issues/245) thanks to [@5chdn](https://github.com/5chdn) for reporting) (PR [#250](https://github.com/PegaSysEng/pantheon/pull/250), PR [#247](https://github.com/PegaSysEng/pantheon/pull/247))
 - Implemented `eth_chainId` JSON-RPC method ([#206](https://github.com/PegaSysEng/pantheon/issues/206)) (PR [#219](https://github.com/PegaSysEng/pantheon/pull/219))
 - Updated client version to be ethstats friendly (PR [#258](https://github.com/PegaSysEng/pantheon/pull/258))
 - Added `--node-private-key` option to allow nodekey file to be specified separately to data directory ([#233](https://github.com/PegaSysEng/pantheon/issues/233) thanks to [@peterbroadhurst](https://github.com/peterbroadhurst) for requesting)  (PR [#234](https://github.com/PegaSysEng/pantheon/pull/234))
 - Added `--banned-nodeids` option to prevent connection to specific nodes (PR [#254](https://github.com/PegaSysEng/pantheon/pull/254))
 - Send client quitting disconnect message to peers on shutdown ([#184](https://github.com/PegaSysEng/pantheon/issues/184)) (PR [#253](https://github.com/PegaSysEng/pantheon/pull/253))
 - Improved error message for port conflict error (PR [#232](https://github.com/PegaSysEng/pantheon/pull/232))
 - Improved documentation by adding the following pages:
    * [Getting Started](https://github.com/PegaSysEng/pantheon/wiki/Getting-Started)
    * [Network ID and Chain ID](https://github.com/PegaSysEng/pantheon/wiki/NetworkID-And-ChainID)
    * [Node Keys](https://github.com/PegaSysEng/pantheon/wiki/Node-Keys) 
    * [Networking](https://github.com/PegaSysEng/pantheon/wiki/Networking) 
    * [Accounts for Testing](https://github.com/PegaSysEng/pantheon/wiki/Accounts-for-Testing) 
    * [Logging](https://github.com/PegaSysEng/pantheon/wiki/Logging) 
    * [Proof of Authority](https://github.com/PegaSysEng/pantheon/wiki/Proof-of-Authority) 
    * [Passing JVM Options](https://github.com/PegaSysEng/pantheon/wiki/Passing-JVM-Options) 


 ### Technical Improvements
 - Upgraded Ethereum reference tests to 6.0 beta 2. (thanks to [@jvirtanen](https://github.com/jvirtanen) for the initial upgrade to beta 1)
 - Set Java compiler default encoding to UTF-8 (PR [#238](https://github.com/PegaSysEng/pantheon/pull/238) thanks to [@matt9ucci](https://github.com/matt9ucci))
 - Removed duplicate code defining default JSON-RPC APIs (PR [#218](https://github.com/PegaSysEng/pantheon/pull/218) thanks to [@matt9ucci](https://github.com/matt9ucci))
 - Improved code for parsing config (PRs [#208](https://github.com/PegaSysEng/pantheon/pull/208), [#209](https://github.com/PegaSysEng/pantheon/pull/209))
 - Use `java.time.Clock` in favour of a custom Clock interface (PR [#220](https://github.com/PegaSysEng/pantheon/pull/220))
 - Improve modularity of storage systems (PR [#211](https://github.com/PegaSysEng/pantheon/pull/211), [#207](https://github.com/PegaSysEng/pantheon/pull/207))
 - Treat JavaDoc warnings as errors (PR [#171](https://github.com/PegaSysEng/pantheon/pull/171))
 - Add benchmark for `BlockHashOperation `as a template for benchmarking other EVM operations (PR [#203](https://github.com/PegaSysEng/pantheon/pull/203))
 - Added unit tests for `EthBlockNumber` ([#189](https://github.com/PegaSysEng/pantheon/issues/189)) (PR [#195](https://github.com/PegaSysEng/pantheon/pull/195) thanks to [@jvirtanen](https://github.com/jvirtanen))
 - Code style improvements (PR [#196](https://github.com/PegaSysEng/pantheon/pull/196) thanks to [@jvirtanen](https://github.com/jvirtanen))
 - Added unit tests for `Web3ClientVersion` ([#191](https://github.com/PegaSysEng/pantheon/issues/191)) (PR [#194](https://github.com/PegaSysEng/pantheon/pull/194) with thanks to [@jvirtanen](https://github.com/jvirtanen))
 - Removed RLPUtils from `RawBlockIterator` (PR [#179](https://github.com/PegaSysEng/pantheon/pull/179))
 - Replace the JNI based snappy library with a pure-Java version (PR [#257](https://github.com/PegaSysEng/pantheon/pull/257))
