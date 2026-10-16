# Release Checklist

Maintainer-facing checklist for cutting a release and for the update path users actually
experience. Keep it in sync with `.github/workflows/release.yml`,
`manager/app/src/main/java/me/weishu/kernelsu/ui/util/Downloader.kt` and `manager/build.gradle.kts`.

## 1. How a release reaches a user

1. Pushing a `v*` tag runs the `Release` workflow, which builds the LKM, `ksuinit`, `ksud` and the
   manager APK, then creates a GitHub Release through `softprops/action-gh-release`.
2. The manager calls `GET https://api.github.com/repos/LyraVoid/FinaSU/releases/latest` every time
   the Home screen becomes visible or the app returns to the foreground (`checkNewVersion()`),
   unless the "Check for updates" setting is off.
3. It picks the first asset whose name ends in `.apk` and parses `v<versionName>_<versionCode>` out
   of the file name (regex `v(.+?)_(\d+)`, pinned by `DownloaderTest`).
4. `HomeUiState.hasUpdate` is just `latestVersionInfo.versionCode > BuildConfig.VERSION_CODE`, so the
   update is offered only when the release carries a strictly higher version code.
5. The Home banner opens the release body as the changelog dialog (or jumps straight to the download
   when the body is empty), then hands the APK URL to the browser. There is no in-app install.

## 2. Version numbers

- `versionCode = 30000 + git rev-list --count HEAD`, in `manager/build.gradle.kts` for the APK and in
  `kernel/Kbuild` for the module, so the APK, `ksud` and the embedded LKM always share one number.
- `versionName = git describe --tags --always`. The tag must sit exactly on the released commit,
  otherwise the name becomes `0.1.0-3-gabc1234` and that is the string shown in the update banner.
- Never release from a commit older than the previous release: a lower version code means nobody is
  offered the update. Backport fixes by merging forward and tagging the new tip.
- Reference point: `v0.1.0-dev1` shipped version code `32817`, while the tip of `main` is already at
  `32916`, so any tag placed on the current tip is offered to every existing installation.

## 3. Tag convention

| Tag | Published as | Reaches users through the update check |
| --- | --- | --- |
| `v0.1.0` | full release | yes, becomes `releases/latest` |
| `v0.1.0-beta1`, `v0.1.0-dev1`, `v0.1.0-rc1` | GitHub pre-release | no, skipped by `releases/latest` |

The workflow derives this from the tag name (`prerelease: ${{ contains(github.ref_name, '-') }}`), so
any hyphen in the tag makes it a test build. This matters because `/releases/latest` returns the most
recently *published* full release: publishing a plain-tagged test build (or anything after the stable
release) takes over the update prompt for everyone.

## 4. Steps

1. Confirm a clean tree and that the release commit is on the remote. The local branch is named `main`
   but tracks `origin/master` with `push.default=upstream`, so publish it with a plain `git push`;
   `git push origin main` would create a second remote branch instead of updating `master`.
2. Tag and push: `git tag -a v0.1.0 -m "FolkSU v0.1.0" && git push origin v0.1.0`.
3. Wait for the `Release` workflow (LKM via DDK, ksuinit, ksud for three Android ABIs plus the desktop
   targets, manager APK, repack and resign, then the GitHub release).
4. Write the release notes. The body is the changelog shown in the manager's update dialog; an empty
   body silently skips that dialog. It can be edited after the workflow finishes, or generated with
   `generate_release_notes: true` on the release step.
5. Run the verification below.
6. Announce it. The Telegram post is automatic when `BOT_TOKEN` and `CHAT_ID` secrets exist; the
   manager picks the rest up by itself on the next app open.

## 5. Verification

```bash
curl -s https://api.github.com/repos/LyraVoid/FinaSU/releases/latest |
  jq -r '.tag_name, .prerelease, (.assets[] | select(.name | endswith(".apk")) | .name)'
```

- `tag_name` is the tag just pushed, `prerelease` is `false` for a stable release.
- The APK asset name follows `<AppName>_<versionName>_<versionCode>-release.apk`; the embedded version
  code must exceed the previous release. A name that does not match the regex (for example
  `app-release.apk`) makes the update invisible to every user.
- One APK asset per release: the parser returns the first match it finds.
- `aapt2 dump badging` on the downloaded APK should report `package: name='me.yuki.fina'` with the
  expected version code and name.
- The signing key must stay the same across releases, otherwise Android refuses to install the update
  over an existing one. The current releases use `manager/key.jks` (CN=FolkSU, certificate SHA-256
  `2d89b50a...4458`), which is also the `KEYSTORE` secret used by CI.

## 6. Kernel-side delivery

- `ksud.yml` stages the built `*_kernelsu.ko` files into `userspace/ksud/bin/<abi>/`, and `repack_apk.py`
  injects `ksud` into the APK, so an APK release also carries a matching LKM.
- Users in LKM mode see "A newer bundled LKM is available. Tap to update." on Home whenever the
  running module version differs from the APK version code; they install it from the Install page and
  reboot, with no PC and no boot image flashing.
- Users running a built-in kernel must rebuild and reflash the boot image themselves. The release
  assets cover that path (`lkm-*_kernelsu.ko` per KMI, `ksud-*`, `ksuinit-*`, zygisk sources).

## 7. Pitfalls

- Do not set `draft: true` on the release step: `action-gh-release` then uploads the assets and never
  publishes the draft.
- Do not cut releases with `workflow_dispatch`. There is no tag, so the release would be named after
  the branch and, being hyphen-free, would publish as a full release and take over `releases/latest`.
  Guarding the `release` job with `if: github.ref_type == 'tag'` is the inverse fix.
- If the repository ever has no published full release (only pre-releases), `releases/latest` does not
  resolve and the manager silently reports no update.
- Every test build published before this convention existed (`v0.1.0-pre4` through `v0.1.0-dev1`) is
  a full release. They stay eligible for `releases/latest` until a newer full release supersedes them.
