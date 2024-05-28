# CP Project: Concurrent Pandemic Simulation – Java Template ☕️

Reference implementation and template for the concurrent programming project 2023.

## Structure

This project is structured as follows:

- `src/main/java/com/pseuco/cp23/`: Java source code of the project.
  - `model/`: Data structures for the simulation.
  - `simulation/rocket/`: Your implementation goes here.
  - `simulation/common/`: Simulation functionality you might find useful.
  - `simulation/slug/`: The sequential reference implementation.
  - `validator/`: The validator interface.
  - `Simulation.java`: Implements the `main` method.
- `src/test`: Public tests for the project.
- `scenarios`: Some example scenarios.

## Gradle

We use [Gradle](https://gradle.org/) to build the project.

To build the Javadoc run:

```bash
./gradlew javaDoc
```

Afterwards you find the documentation in `build/docs`.

To build a `simulation.jar`-File for your project run:

```bash
./gradlew jar
```

You find the compiled `.jar`-File in `out`.

To run the _public_ tests on your project run:

```bash
./gradlew test
```

## Integrated Development Environment

We recommend you use a proper _Integrated Development Environment_ (IDE) for this project. A good open source IDE is [VS Code](https://code.visualstudio.com/). Which IDE or editor you use is up to you. However, we only provide help for VS Code. In case you use something else, do not expect help.

In case you decide to use VS Code, open the `vscode.code-workspace` workspace. After opening the workspace, VS Code should ask you whether you want to install the *recommended extensions*. For maximal convenience, please do so. In particular, the *Extension Pack for Java* extension is highly recommended.

