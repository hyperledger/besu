---
name: Release Checklist
about: items to be completed for each release
title: ''
labels: ''
assignees: ''

---

- [ ] Confirm anything outstanding for release with other maintainers on #besu-release in Discord
- [ ] Update changelog if necessary, and merge a PR for it to main
  - [ ] Notify maintainers about updating changelog for in-flight PRs 
- [ ] Optional: for hotfixes, create a release branch and cherry-pick, e.g. `release-<version>-hotfix`
- [ ] Optional: create a PR into main from the hotfix branch to see the CI checks pass
- [ ] On the appropriate branch/commit, create a calver tag for the release candidate, format example: `24.4.0-RC2`
- [ ] Sign-off with team; confirm tag is correct in #besu-release in Discord
- [ ] Consensys staff start burn-in using the proposed release <version-RCX> tag
- [ ] Sign off burn-in; convey burn-in results in #besu-release in Discord
- [ ] Using the same git sha, create a calver tag for the FULL RELEASE, example format `24.4.0`
- [ ] Using the FULL RELEASE tag, create a release in github to trigger the workflows. Once published:
    - this is now public and notifies subscribed users
    - makes the release "latest" in github
    - publishes artefacts and version-specific docker tags
    - publishes the docker `latest` tag variants
- [ ] Check binary SHAs are correct on the release page
- [ ] Check "Container Verify" GitHub workflow has run successfully
- [ ] Create homebrew release - run https://github.com/hyperledger/homebrew-besu/actions/workflows/update-version.yml
- [ ] Create besu-docs release - https://github.com/hyperledger/besu-docs/releases/new
   - Copy release notes from besu
   - If publishing the release in github doesn't automatically trigger this workflow, then manually run https://github.com/hyperledger/besu-docs/actions/workflows/update-version.yml
- [ ] Social announcements
