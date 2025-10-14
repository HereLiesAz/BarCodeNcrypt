# File Descriptions

This file provides a brief but thorough description of all non-ignored files in the project.

## Root Directory

- **.gitignore**: Specifies intentionally untracked files to ignore. This file is used by Git to determine which files and directories to ignore, before you make a commit.
- **build.gradle.kts**: This is the top-level build file for the entire project. It's used to define the build configurations that apply to all modules in the project.
- **gradle.properties**: This file is used to configure project-wide Gradle settings, such as the JVM arguments for the Gradle daemon, and AndroidX properties.
- **gradlew**: The Gradle wrapper script for Unix-based systems. This script allows you to run Gradle tasks without having to install Gradle on your system.
- **gradlew.bat**: The Gradle wrapper script for Windows. This script allows you to run Gradle tasks without having to install Gradle on your system.
- **settings.gradle.kts**: This file is used to define the project structure, and to include the modules that are part of the project.

## App Directory

- **.gitignore**: Specifies that the `build` directory within the `app` directory should be ignored by Git.
- **build.gradle.kts**: This is the module-level build file for the `app` module. It's used to configure the build settings for the app, including dependencies, plugins, and Android-specific settings.
- **google-services.template.json**: This is a template file for the `google-services.json` file. It's used to configure Google services for the app.
- **proguard-rules.pro**: This file is used to configure ProGuard, which is a tool that shrinks, optimizes, and obfuscates your code by removing unused code and renaming classes, fields, and methods with semantically obscure names.

## Docs Directory

- **INDEX.md**: This file is the table of contents for the documentation. It provides a list of all the documentation files and links to them.
- **UI_UX.md**: This file describes the user interface and user experience of the app. It covers the OverlayService, the interactive tutorial, and the user experience for key exchange.
- **auth.md**: This file describes the different key types and password protection mechanisms in the app.
- **conduct.md**: This file outlines the code of conduct for contributors to the project.
- **data_layer.md**: This file describes the data storage, cryptographic model, and key generation mechanisms of the app.
- **fauxpas.md**: This file lists common mistakes and anti-patterns to avoid when working on the codebase.
- **misc.md**: This file contains miscellaneous information about the app that does not fit into other categories.
- **performance.md**: This file discusses performance considerations, particularly related to the background services that power the app.
- **screens.md**: This file describes the main screens and UI components of the app.
- **task_flow.md**: This file outlines the step-by-step user flows for key management, encryption, and decryption in the app.
- **testing.md**: This file outlines the testing strategy for the app.
- **workflow.md**: This file explains the encryption and decryption process in the app.
