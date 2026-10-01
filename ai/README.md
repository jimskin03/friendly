# Environment Setup

## Prerequisites

- Install CMake
- Install NDK and configure the `ANDROID_NDK` environment variable

## Git Submodule

Run the following command at the repository root to initialize submodules:

```bash
git submodule update --init --recursive
```

Note: This command must be executed in the project root directory, not in the `src/main/cpp/mnn` directory.

## Build libMNN.so

Navigate to the `src/main/cpp/mnn` directory and run:

```bash
./build.sh
```
