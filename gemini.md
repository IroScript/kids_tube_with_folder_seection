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
