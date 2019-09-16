description: Community organisation and members responsibilities
<!--- END of page meta data -->

!!!note
    This document is in progress

This doc outlines the various responsibilities of contributor roles in
this project.

| Role | Responsibilities | Defined by |
| -----| ---------------- | ---------- |
| Everyone | none | anybody with a belly button
| Member | everyone who contributes - code or otherwise | Besu GitHub org member
| Approver | approve accepting contributions | write permissions on master
| Project Manager | management of the project | Hyperledger
| Project Sponsor | contribute developer resources | Hyperledger
| Open Source Circle | OSS support | Hyperledger
| Project Evangelist | promote the project | Hyperledger
| Benevolent Dictator | decision tie-breaker | Hyperledger

## Everyone
Any person from the public is able to access the code.  The standard permissions grant the ability to view the code, view open bugs, access the wiki, download binaries, view CI results and comment on pull requests.

## New contributors

[New contributors] should be welcomed to the community by existing members,
helped with PR workflow, and directed to relevant documentation and
communication channels.

## Established community members

Established community members are expected to demonstrate their adherence to the
principles in this document, familiarity with project organization, roles,
policies, procedures, conventions, etc., and technical and/or writing ability.
Role-specific expectations, responsibilities, and requirements are enumerated
below.

## Member

Members are continuously active contributors in the community.  They can have
issues and PRs assigned to them.

### Requirements

- Enabled [two-factor authentication] on their GitHub account
- Have made multiple contributions to the project or community.  Contribution may include, but is not limited to:
    - Authoring or reviewing PRs on GitHub
    - Filing or commenting on issues on GitHub
    - Contributing to community discussions (e.g. meetings, Slack, email discussion forums, Stack Overflow)
- Subscribed to [besu-dev@pegasys.tech]
- Joined [Besu Rocketchat]
- Browsed [Besu Wiki]
- Have read the [contributor guide]
- Signed ICLA, as described in [CLA.md]

### Responsibilities and privileges

- Responsive to issues and PRs assigned to them
- Active owner of code they have contributed (unless ownership is explicitly transferred)
  - Code is well tested
  - Tests consistently pass
  - Addresses bugs or issues discovered after code is accepted
- Members can do `/lgtm` on open PRs
- They can be assigned to issues and PRs, and people can ask members for reviews with a `/cc @username`

## Approver

Code approvers are members that have signed an ICLA and have been granted additional commit privileges. While members are expected to provided code reviews that focus on code quality and correctness, approval is focused on holistic acceptance of a contribution including: backwards / forwards compatibility, adhering to API and flag conventions, subtle performance and correctness issues, interactions with other parts of the system, etc.

**Defined by:** write permissions on master branch

### Requirements

- Includes all of the requirements of a Member user
- Signed ICLA, as described in [CLA.md]
- Approver status granted by Project Sponsor or the Open Source Circle

### Responsibilities and privileges
- Includes all of the responsibilities and privileges of a Member user
- Approver status may be a precondition to accepting large code contributions
- Demonstrate sound technical judgement
- Responsible for project quality control via [code reviews]
  - Focus on holistic acceptance of contribution such as dependencies with other features, backwards / forwards
    compatibility, API and flag definitions, etc
- Expected to be responsive to review requests as per [community expectations]
- Mentor members
- May approve pull requests
- May merge pull requests

## Project Manager
The Project Manager role provides the user with control over management aspects of the project.  

**Defined by:** Hyperledger

### Requirements
- Includes all of the requirements of a Member user
- Signed ICLA, as described in [CLA.md]
- PM status granted by Project Sponsor or the Open Source Circle

### Responsibilities and privileges
- Includes all of the responsibilities and privileges of a Member user
- Determining releases
- Managing roadmaps and access to Circle reports



## Project Sponsor
The Project Sponsor role provides a user with the ability to contribute additional developer resources to the project.  Project Sponsors must sign the ICLA.

**Defined by:** Hyperledger

### Requirements
- Signed ICLA, as described in [CLA.md]
- Project Sponsor status granted by the Open Source Circle

### Responsibilities and privileges
- Includes all of the responsibilities and privileges of a Member user
- Approval of new users to the Approver role, and access to Circle reports.

## Open Source Circle
The Open Source Circle is a group that provides open source software support to projects.
**Defined by:** Hyperledger

### Requirements
- Includes all of the requirements of a Member user
- Signed ICLA, as described in [CLA.md]
- Open Source Circle status granted by the Open Source Circle

### Responsibilities and privileges
- Includes all of the responsibilities and privileges of a Project Sponsor
- Ability to archive the project
- Manage the CLA
- Conduct legal reviews

## Project Evangelist
The Project Evangelist role is for those who wish to promote the project to the outside world, but not actively contribute to it.  
**Defined by:** Hyperledger

### Requirements
- Includes all of the requirements of a Member user
- Signed ICLA, as described in [CLA.md]
- Project Evangelist status granted by the Open Source Circle

### Responsibilities and privileges
- Includes all of the responsibilities and privileges of a Member user
- Project Evangelist have the standard public access permissions
- Organise talks
- Work with marketing to manage web and graphical assets

## Benevolent Dictator
The benevolent dictator, or project lead, is self-appointed. However, because the community always has the ability to fork, this person is fully answerable to the community.  The key is to ensure that, as the project expands, the right people are given influence over it and the community rallies behind the vision of the project lead.

### Responsibilities and privileges
- Set the strategic objectives of the project and communicate these clearly to the community
- Understand the community as a whole and strive to satisfy as many conflicting needs as possible, while ensuring that the project survives in the long term
- Ensure that the approvers make the right decisions on behalf of the project
- Provide final tie-breaker decisions when consensus cannot be reached.


## Attribution

This document is adapted from the following sources:
- Kubernetes community-membership.md, available at [kub community membership].  
- OSSWatch Benevolent Dictator Governance Model, available at [oss watch benevolent dictator].  

[CLA.md]: /CLA.md
[oss watch benevolent dictator]: http://oss-watch.ac.uk/resources/benevolentdictatorgovernancemodel
[kub community membership]: https://raw.githubusercontent.com/kubernetes/community/master/community-membership.md
[code reviews]: /docs/community/code-reviews.md
[contributor guide]: /CONTRIBUTING.md
[New contributors]: /CONTRIBUTING.md
[two-factor authentication]: https://help.github.com/articles/about-two-factor-authentication
[besu-dev@pegasys.tech]: mailto:besu-dev@pegasys.tech
[Besu RocketChat]: https://chat.hyperledger.org/channel/besu
[Besu Documentation]: https://besu.readthedocs.io/
