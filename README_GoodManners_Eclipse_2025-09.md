
# GoodManners — README & Eclipse 2025‑09 Run Guide (Java 24 + JavaFX 24)

**Author:** Tennie White  
**App:** GoodManners (JavaFX educational game)  
**Tested With:** Java 24 (JDK 24), JavaFX 24, Eclipse 2025‑09 (no Maven/Gradle)

---

## 1) What This App Does
GoodManners shows child‑friendly social scenarios with **prompt audio**, then lets the learner choose **Good** or **Bad**. It plays **feedback audio**, shows a **pulsing feedback panel**, and (optionally) shows a **frame animation**. Scenarios come from `assets/scenarios.json`.

---

## 2) Requirements

- **Java Development Kit (JDK) 24**
- **JavaFX SDK 24**
- **Eclipse 2025‑09** (or newer)
- **org.json** library JAR (e.g. `json-20240303.jar`) for JSON parsing
- System audio output for sounds (JavaFX Media)

> **No Maven/Gradle.** These instructions use plain Eclipse configuration.

---

## 3) Project Layout (expected)

```
GoodManners/                        ← Eclipse project root
├─ src/
│  └─ com/behavior/goodmanners/
│     └─ GoodManners.java           ← main class (package must match path)
├─ assets/
│  ├─ scenarios.json                ← scenario list
│  ├─ background_manners.png        ← optional end screen
│  ├─ check.png, x.png, playagain.png, exit.png
│  ├─ (image frames for animations, optional)
│  └─ (audio files referenced by JSON)
└─ lib/
   └─ json-20240303.jar             ← org.json (or similar version)
```

> If your current layout differs, adjust file paths in `GoodManners.java` or move files to match.

---

## 4) Import Into Eclipse 2025‑09

### Option A — Import from an **existing folder**
1. **File → Import… → General → Existing Projects into Workspace → Next**
2. **Select root directory:** browse to your `GoodManners` folder
3. Ensure the project appears in the list → **Finish**

### Option B — Import from an **archive (.zip)**
1. **File → Import… → General → Existing Projects into Workspace → Next**
2. **Select archive file:** pick `GoodManners.zip`
3. Select the project → **Finish**

> After import, verify `src/com/behavior/goodmanners/GoodManners.java` exists and starts with:  
> `package com.behavior.goodmanners;`

---

## 5) Add JavaFX 24 to the **Modulepath**

JavaFX is not in the JDK. You must download **JavaFX SDK 24** and tell Eclipse where it is.

1. Download JavaFX SDK 24 and unzip:
   - Example (Windows): `C:\javafx-sdk-24\lib`
   - Example (macOS): `/Library/Java/javafx-sdk-24/lib` (or your chosen path)

2. In Eclipse: **Project → Properties → Java Build Path → Libraries** (or **Modules**)
   - Click **Modulepath** (not Classpath).
   - **Add External JARs…** → select **all JARs** inside your `javafx-sdk-24/lib` folder.
   - **Apply and Close**.

> If Eclipse offers **User Libraries**, you may create one named `JavaFX24` and add it to the **Modulepath**.

---

## 6) Add the **org.json** Library

Your code imports `org.json` for parsing `scenarios.json`.

- Put a copy of `json-20240303.jar` (or similar) in your project `lib/` folder (or anywhere you prefer).
- **Project → Properties → Java Build Path → Libraries → Add External JARs…**
  - Add your `json-*.jar` to the **Classpath** (if you are **not** using `module-info.java`), or to the **Modulepath** (if you use JPMS).
- **Apply and Close**.

> If using JPMS (`module-info.java`), Eclipse may detect the module name as `org.json`. Add `requires org.json;` to your module.

---

## 7) Choose **one** of two configurations

You can run with or without `module-info.java`. Both work. **Pick one**:

### **Option 1 (Recommended Beginner): No `module-info.java`**
- If your project has a `module-info.java`, **delete** it.
- Keep JavaFX JARs on the **Modulepath** (from step 5).
- Add `org.json` JAR to the **Classpath** (step 6).

### **Option 2 (JPMS / Modules): With `module-info.java`**
Create or edit `module-info.java` to match your module name (here we show `GoodManners` as an example):

```java
module GoodManners {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.media;
    // If Eclipse resolves your JSON jar as the 'org.json' module, then include:
    // requires org.json;

    exports com.behavior.goodmanners;
    // opens com.behavior.goodmanners to javafx.graphics; // only if you use reflection that needs it
}
```

> Even with modules, you still need to pass JavaFX to the JVM at runtime (`--module-path … --add-modules …`).

---

## 8) Create the Run Configuration (Java Application)

1. **Run → Run Configurations… → Java Application → New launch configuration**
2. **Project:** select your `GoodManners` project
3. **Main class:** `com.behavior.goodmanners.GoodManners`

### Arguments tab → **VM arguments** (single line — no carets `^`)

- **Windows example:**
  ```
  --module-path "C:\javafx-sdk-24\lib" --add-modules=javafx.controls,javafx.graphics,javafx.media --enable-native-access=javafx.graphics
  ```

- **macOS/Linux example (adjust path):**
  ```
  --module-path "/Library/Java/javafx-sdk-24/lib" --add-modules=javafx.controls,javafx.graphics,javafx.media --enable-native-access=javafx.graphics
  ```

**Important:**
- Keep this as **one single line** in Eclipse. Do **not** use Windows `^` line continuations in VM args — that causes `ClassNotFoundException: ^`.
- Include **`javafx.media`** because this app plays sounds.
- `--enable-native-access=javafx.graphics` silences Java 24’s “restricted method” warning for JavaFX.

Click **Apply** → **Run**.

---

## 9) First Run Checklist

- ✅ Console shows the app starts without `NoClassDefFoundError` or `Could not find or load main class`.
- ✅ A window titled **“Good Manners Game”** appears.
- ✅ Background images draw; prompt text displays.
- ✅ After ~1.5s, Good/Bad buttons appear.
- ✅ Clicking Good/Bad plays audio and shows a pulsing feedback panel.
- ✅ End screen shows after the last scenario.

---

## 10) Troubleshooting (fast fixes)

**Error:** `Could not find or load main class com.behavior.goodmanners.GoodManners`  
- Check **package vs folder**: `src/com/behavior/goodmanners/GoodManners.java`  
- Check **Run Config → Main class** matches exactly.
- If the error is `… main class ^` → remove any `^` characters from **VM arguments**.

**Error:** `NoClassDefFoundError: javafx/application/Application`  
- JavaFX not on runtime path. Re‑check **VM arguments** and **Modulepath** JARs.

**Warning:** “Restricted method … enable native access”  
- Add `--enable-native-access=javafx.graphics` to **VM arguments**.

**Silent audio / Media errors**  
- Ensure audio file paths in `assets/scenarios.json` match real files.  
- Use supported formats (e.g., WAV/MP3).

**Animation doesn’t show**  
- Verify `frames_pattern` and `frame_count` in the JSON resolve to real image files.

**JSON parsing errors**  
- Confirm `assets/scenarios.json` exists and is valid JSON.  
- Check that each scenario has required fields used by the app:
  - `prompt_text`, `prompt_audio`, `background`, `background_feedback`  
  - `positive.feedback_text`, `positive.audio`  
  - `negative.feedback_text`, `negative.audio`  
  - Optional: `positive.frames_pattern` + `frame_count`, `negative.frames_pattern` + `frame_count`

---

## 11) Customizing Assets & Paths

- Default paths in code expect assets under `assets/`.  
- If you move assets, update the paths in `GoodManners.java` or in `scenarios.json` accordingly.
- End screen uses `assets/background_manners.png` if present.

---

## 12) Clean & Rebuild

If Eclipse gets “stuck”:
- **Project → Clean… → Clean all projects**  
- Then **Run** again.

---

## 13) Known Good VM Arguments (copy‑paste)

**Windows:**
```
--module-path "C:\javafx-sdk-24\lib" --add-modules=javafx.controls,javafx.graphics,javafx.media --enable-native-access=javafx.graphics
```

**macOS (Homebrew style example):**
```
--module-path "/opt/homebrew/opt/javafx/lib" --add-modules=javafx.controls,javafx.graphics,javafx.media --enable-native-access=javafx.graphics
```

**Linux (example):**
```
--module-path "/usr/lib/javafx/lib" --add-modules=javafx.controls,javafx.graphics,javafx.media --enable-native-access=javafx.graphics
```

---

## 14) Optional: Using `module-info.java` (JPMS) — Full Example

If your Eclipse **module name** is `GoodManners`, this file should be in `src/module-info.java`:

```java
module GoodManners {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.media;
    // Uncomment if Eclipse resolves your json jar as module 'org.json':
    // requires org.json;

    exports com.behavior.goodmanners;
    // opens com.behavior.goodmanners to javafx.graphics; // only if reflective access is needed
}
```

> With JPMS, keep the same **VM arguments** and ensure all JavaFX jars are on the **Modulepath**.

---

## 15) Support Notes

- Use **single‑line VM args** in Eclipse (no `^`).
- Keep **JavaFX JARs** on **Modulepath**.
- If you switch JDKs, revisit your **Run Config** and **Build Path**.
- If you refactor packages/folders, update the **Main class** in Run Config.

Happy coding and teaching! 🎉
