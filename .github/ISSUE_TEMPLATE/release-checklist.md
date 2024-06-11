---
name: Release Checklist
about: items to be completed for each release
title: ''
labels: ''
assignees: ''

---

- [ ] Confirm anything outstanding for release with other maintainers on #besu-release in Discord
  - [ ] Notify maintainers about updating changelog for in-flight PRs 
  - [ ] Update changelog if necessary, and merge a PR for it to main
- [ ] Optional: for hotfixes, create a release branch and cherry-pick, e.g. `release-<version>-hotfix`
- [ ] Optional: create a PR into main from the hotfix branch to see the CI checks pass
- [ ] On the appropriate branch/commit, create a calver tag for the release candidate, format example: `24.4.0-RC2`
- [ ] Sign-off with team; confirm tag is correct in #besu-release in Discord
- [ ] Consensys staff start burn-in using the proposed release <version-RCX> tag
- [ ] Sign off burn-in; convey burn-in results in #besu-release in Discord
- [ ] Using the same git sha, create a calver tag for the FULL RELEASE, example format `24.4.0`
- [ ] Using the FULL RELEASE tag, create a release in github to trigger the workflows. Once published:
    - makes the release "latest" in github
    - this is now public and notifies subscribed users
    - publishes artefacts and version-specific docker tags
    - publishes the docker `latest` tag variants
- [ ] Draft homebrew PR
- [ ] Draft documentation release
- [ ] Ensure binary SHAs are correct on the release page
- [ ] Docker release startup test:
    - `docker run hyperledger/besu:<version>`
    - `docker run hyperledger/besu:<version>-arm64`
    - `docker run --platform linux/amd64 hyperledger/besu:<version>-amd64`
    - `docker run --pull=always hyperledger/besu:latest` (check version is <version>)
- [ ] Merge homebrew PR
- [ ] Publish Docs Release
- [ ] Social announcements
