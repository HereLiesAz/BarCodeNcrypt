# File Descriptions

This file provides a brief but thorough description of all non-ignored files in the project.

## Root Directory

- **.gitignore**: Specifies intentionally untracked files to ignore. This file is used by Git to determine which files and directories to ignore, before you make a commit. This file is crucial for keeping the repository clean and avoiding the submission of unnecessary files, such as build artifacts and local configuration files.
- **build.gradle.kts**: This is the top-level build file for the entire project. It's used to define the build configurations that apply to all modules in the project.
- **gradle.properties**: This file is used to configure project-wide Gradle settings, such as the JVM arguments for the Gradle daemon, and AndroidX properties.
- **gradlew**: The Gradle wrapper script for Unix-based systems. This script allows you to run Gradle tasks without having to install Gradle on your system.
- **gradlew.bat**: The Gradle wrapper script for Windows. This script allows you to run Gradle tasks without having to install Gradle on your system.
- **settings.gradle.kts**: This file is used to define the project structure, and to include the modules that are part of the project.

## App Directory

- **.gitignore**: Specifies that the `build` directory within the `app` directory should be ignored by Git.
- **build.gradle.kts**: This is the module-level build file for the `app` module. It's used to configure the build settings for the app, including dependencies, plugins, and Android-specific settings.
- **google-services.template.json**: This is a template file for the `google-services.json` file. It's used to configure Google services for the app.
- **proguard-rules.pro**: This file is used to configure ProGuard, which is a tool that shrinks, optimizes, and obfuscates your code.

## Docs Directory

- **INDEX.md**: The main table of contents for the documentation.
- **UI_UX.md**: Describes the user interface and user experience, including the on-screen detection and decryption process.
- **auth.md**: Describes the app's security model, including barcode and password-based keys.
- **conduct.md**: Outlines the code of conduct for contributors to the project.
- **data_layer.md**: Describes the app's cryptographic model, data storage, and key generation.
- **fauxpas.md**: Lists common mistakes and anti-patterns to avoid when working on the codebase.
- **misc.md**: Contains miscellaneous information about the app that does not fit into other categories.
- **performance.md**: Discusses performance considerations, particularly related to the background services that power the app.
- **screens.md**: Describes the main screens and UI components of the app.
- **task_flow.md**: Outlines the step-by-step user flows for key management, encryption, and decryption.
- **testing.md**: Outlines the testing strategy for the app.
- **workflow.md**: Explains the encryption and decryption process in the app.
