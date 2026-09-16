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

