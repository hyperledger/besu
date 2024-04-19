---
name: Release Checklist
about: items to be completed for each release
title: ''
labels: ''
assignees: ''

---

- [ ] Confirm anything outstanding for release with other maintainers on #besu-release
- [ ] Update changelog if necessary
- [ ] Notify maintainers about updating changelog for in-flight PRs 
- [ ] Optional: for hotfixes, create a release branch and cherry-pick, e.g. `release-<version>-hotfix`
- [ ] Optional: create a PR against the hotfix branch to see the CI checks pass
- [ ] On the appropriate branch/commit, create a calver tag for the release candidate, format example: 24.4.0-RC2
- [ ] Create a DRAFT release using the new tag (careful not to publish!)
- [ ] Draft homebrew PR
- [ ] Draft documentation release
- [ ] Sign-off with team
- [ ] Start burn-in using the new <version-RCX> tag
- [ ] Sign off burn-in
- [ ] Convert the DRAFT into a PRE-RELEASE
    - this is now public and notifies subscribed users
    - publishes artefacts and version-specific docker tags
- [ ] Using the same git sha, create a new tag for the FULL RELEASE, example format 24.4.0
- [ ] Using the FULL RELEASE tag, create a PRE-RELEASE in github to trigger the workflows
- [ ] Convert this to a FULL RELEASE
    - makes the release LATEST in github
    - publishes the docker `latest` tag variants
- [ ] Ensure binary SHAs are correct on the release page
- [ ] Docker release startup test:
    - `docker run hyperledger/besu:<version>`
    - `docker run hyperledger/besu:<version>-arm64`
    - `docker run --platform linux/amd64 hyperledger/besu:<version>-amd64`
    - `docker run --pull=always hyperledger/besu:latest` (check version is <version>)
- [ ] Merge homebrew PR
- [ ] Publish Docs Release
- [ ] Social announcements
