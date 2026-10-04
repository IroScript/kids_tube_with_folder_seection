# KidsTube Project-Specific Directives & Rules

> ⚠️ **PROJECT-SPECIFIC EXCEPTION MANDATE (APPLIES ONLY TO KidsTube: C:\Users\Irak\Desktop\KidsTube & /home/mdkamruzzamanirak_gmail_com/kids_tube_with_folder_seection)** ⚠️

---

## 1. REPOSITORY SCOPE & AUTHORIZATION
* **Target Project:** KidsTube (`C:\Users\Irak\Desktop\KidsTube` / `/home/mdkamruzzamanirak_gmail_com/kids_tube_with_folder_seection`)
* **Authorized By:** ইরাক ভাইয়া

---

## 2. AUTO GIT PUSH DIRECTIVE (THIS PROJECT ONLY)
* **Global Rule Context:** Global agent directives (AGENTS.md / GEMINI.md) generally prohibit pushing to Git without explicit user commands.
* **KidsTube Project Exception:** For this repository, the user has explicitly authorized and mandated **Auto Git Push on every completed turn/reply** so that GitHub Actions CI can immediately trigger and build the Android APK for device testing.
* **Strict Safety Mandate:**
  1. All code changes and tests MUST pass 100% verification before committing.
  2. Verify GitHub remote availability (git ls-remote origin main).
  3. Commit cleanly with descriptive commit messages.
  4. Push to origin (git push origin main).
  5. Never execute destructive operations (git reset --hard, git push --force, or deleting history).

---

## 3. GLOBAL CONFIGURATION PROTECTION
* **NEVER TOUCH GLOBAL FILES:** Never modify, overwrite, or delete any global files in C:\Users\Irak\.gemini\... or user-level configuration documents.
* This rule is strictly isolated to the KidsTube repository.

---

## 4. MANDATORY APK BUILD AUDIT & AGY TASK COMPLETION RULE
* **Core Principle:** AGY's task is NOT finished until the Android APK build is 100% verified and successfully generated.
* **Role Distinction:**
  - **User (ইরাক ভাইয়া):** The user's role is ONLY to test the completed, built APK on devices. The user will NOT debug or monitor build failures.
  - **AGY (Antigravity AI):** AGY MUST audit and verify that the APK build actually succeeds. If the build fails or encounters issues, AGY MUST fix the build issues immediately. AGY cannot consider the turn or task finished until the APK is successfully generated.
* **Verification Protocol:**
  1. Local Build: Verify `./gradlew :app:assembleDebug` builds cleanly (`app-debug.apk`).
  2. Cloud CI Build: After pushing to GitHub, AGY MUST query the GitHub Actions API (`https://api.github.com/repos/IroScript/kids_tube_with_folder_seection/actions/runs`) and audit that the `Build Android APK` workflow completes with `status: completed` and `conclusion: success`, and that the `KidsTube-APK` artifact is generated.
  3. **Task Completion Standard:** "Buildup done = AGY er kaaj shesh". Only when the APK build is verified as successful, AGY reports completion to Iraq bhai for device testing.

---

## 5. LOCAL APK BUILD & CLEANUP STANDARD (DELETE OLD APK BEFORE BUILD)
* **Pre-Build Cleanup:** Before building a new APK locally, always scan and delete any pre-existing/old APK files (`find . -name "*.apk" -ls -delete` or Gradle clean) to ensure no stale artifact remains.
* **Local Build Execution:** Execute the local build function using the designated JDK environment:
  ```bash
  JAVA_HOME=/home/azureuser/.local/jdk-21 ./gradlew assembleDebug --no-daemon
  ```
* **Post-Build Artifact Verification:** Confirm that the output artifact physically exists with non-zero byte size at:
  `app/build/outputs/apk/debug/app-debug.apk`

---

## 6. MANDATORY LOCAL HIGH-SPEED APK DOWNLOAD LINK STANDARD (LOCAL SERVER FIRST)
* **Core Mandate:** Whenever providing APK files, downloads, or build artifacts for KidsTube (`AGY · Kids Tube (kids)`), the agent MUST ALWAYS provide the direct high-speed Cloudflare tunnel download link served directly from the local Azure VM filesystem (`https://<active_tunnel>/api/raw?path=/home/azureuser/IroScript_Projects/Personal%20Life/kids_tube_with_folder_seection/KidsTube-debug.apk&download=1`).
* **Strict Prohibition of GitHub CI Download as Primary:** Providing GitHub Actions CI artifact links or GitHub repo links as the primary download source is strictly prohibited. Local server downloads offer significantly higher transfer speed (25+ MB/s), instant availability upon local build without waiting for CI queues, and zero GitHub login requirement. GitHub Actions remain strictly secondary for automated background CI logging.
* **Active Local Server & Tunnel Endpoints:**
  - Root Symlink Download: `https://<active_tunnel>/api/raw?path=/home/azureuser/IroScript_Projects/Personal%20Life/kids_tube_with_folder_seection/KidsTube-debug.apk&download=1`
  - Gradle Output Download: `https://<active_tunnel>/api/raw?path=/home/azureuser/IroScript_Projects/Personal%20Life/kids_tube_with_folder_seection/app/build/outputs/apk/debug/app-debug.apk&download=1`
  - Active Tunnel Source: `/home/azureuser/IroScript_Projects/Whatsapp_Agy_Agents/Antigravity-Global-Notifier/Cloud_VM_Live_Preview/tunnel_url.txt`
* **Upfront Presentation:** In every reply regarding KidsTube APK builds or download requests, the local server download link MUST be presented at the very top of the response for instant single-click downloading on mobile and desktop.

---

## 7. ROCK-SOLID POPUP STABILITY & PERSISTENCE MANDATE
* **Core Mandate:** All popup windows, parent gates, and settings dialogs (specifically `ParentControlsSheet` and `ParentLockDialog`) MUST be implemented using persistent `Dialog` components with `dismissOnClickOutside = false` and `dismissOnBackPress = false`.
* **Zero Auto-Dismissal:** Popups are strictly prohibited from auto-dismissing, collapsing on gesture/swipe, or fading out without explicit user interaction.
* **Deterministic Single & Repeated Click Entry:** Folder Management and Parent Dashboard buttons MUST work every single time they are clicked (1st, 2nd, 3rd, and subsequent clicks). When a dialog is dismissed via its explicit Cross [X] button or Done button, the ViewModel state (`isParentMode`, `isFolderManagerMode`) must cleanly reset so that subsequent clicks immediately re-open the dialog without getting stuck.
