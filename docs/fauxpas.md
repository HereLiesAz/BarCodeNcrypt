# Faux Pas

This document lists common mistakes and anti-patterns to avoid when working on the Barcodencrypt codebase.

*Avoid introducing third-party libraries without prior approval. The project aims to have minimal dependencies.*
*Do not implement custom cryptography. Rely on well-vetted, standard implementations.*
*Ensure that no sensitive information, such as keys or personal data, is logged.*