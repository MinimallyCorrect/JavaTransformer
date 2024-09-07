JavaTransformer
====
<!---freshmark shields
output = [
	link(image('Release notes', 'https://img.shields.io/maven-metadata/v?label=changelog&metadataUrl=https%3A%2F%2Fjcenter.bintray.com%2F' + '{{group}}'.replaceAll("\\.", "%2F") + '%2F{{name}}%2Fmaven-metadata.xml'), '{{releaseNotesPath}}'),
	link(shield('Maven artifact', 'jcenter', '{{name}}', 'blue'), 'https://bintray.com/{{bintrayrepo}}/{{name}}/view'),
	link(image('License', 'https://img.shields.io/github/license/{{organisation}}/{{name}}.svg'), 'LICENSE') + '  ',
	link(image('Travis CI', 'https://travis-ci.org/{{organisation}}/{{name}}.svg'), 'https://travis-ci.org/{{organisation}}/{{name}}'),
	link(image('Coverage', 'https://img.shields.io/lgtm/alerts/g/{{organisation}}/{{name}}'), 'https://lgtm.com/projects/g/{{organisation}}/{{name}}'),
	link(image('Coverage', 'https://img.shields.io/codecov/c/github/{{organisation}}/{{name}}.svg'), 'https://codecov.io/gh/{{organisation}}/{{name}}/') + '  ',
	link(image('Discord chat', 'https://img.shields.io/discord/{{discordId}}?logo=discord'), '{{discordInvite}}'),
	].join('\n');
-->
[![Release notes](https://img.shields.io/maven-metadata/v?label=changelog&metadataUrl=https%3A%2F%2Fjcenter.bintray.com%2Forg%2Fminimallycorrect%2Fjavatransformer%2FJavaTransformer%2Fmaven-metadata.xml)](docs/release-notes.md)
[![Maven artifact](https://img.shields.io/badge/jcenter-JavaTransformer-blue.svg)](https://bintray.com/minimallycorrect/minimallycorrectmaven/JavaTransformer/view)
[![License](https://img.shields.io/github/license/MinimallyCorrect/JavaTransformer.svg)](LICENSE)  
[![Travis CI](https://travis-ci.org/MinimallyCorrect/JavaTransformer.svg)](https://travis-ci.org/MinimallyCorrect/JavaTransformer)
[![Coverage](https://img.shields.io/lgtm/alerts/g/MinimallyCorrect/JavaTransformer)](https://lgtm.com/projects/g/MinimallyCorrect/JavaTransformer)
[![Coverage](https://img.shields.io/codecov/c/github/MinimallyCorrect/JavaTransformer.svg)](https://codecov.io/gh/MinimallyCorrect/JavaTransformer/)  
[![Discord chat](https://img.shields.io/discord/313371711632441344?logo=discord)](https://discord.gg/YrV3bDm)
<!---freshmark /shields -->

Applies transformations to .java and .class files using a unified high level API.

# Internal Architecture and Implementation

## Project Structure

JavaTransformer is organized into several key packages:

- `dev.minco.javatransformer.api`: Public API interfaces and classes
- `dev.minco.javatransformer.internal`: Internal implementation details
- `dev.minco.javatransformer.internal.asm`: ASM-specific utilities and implementations
- `dev.minco.javatransformer.internal.util`: General utility classes

## Key Concepts

- **Unified Abstraction**: The project provides a unified interface (`ClassInfo`, `MethodInfo`, `FieldInfo`) for working with both bytecode and source code, allowing transformers to be written agnostic of the underlying format.

- **Multi-format Support**: JavaTransformer can load, transform, and save both bytecode (.class) and source code (.java) files, seamlessly switching between formats as needed.

- **Flexible Transformation**: The transformer system allows for both general transformers (applied to all classes) and targeted transformers (applied to specific classes), providing fine-grained control over the transformation process.

- **Code Fragment Insertion**: The `CodeFragment` system allows for inserting new code into existing methods, supporting both bytecode and source code formats. This enables complex transformations that go beyond simple structural changes.

- **Type Resolution**: The `ResolutionContext` and `ClassPath` components provide robust type resolution capabilities, ensuring that transformations can accurately work with types across different classes and packages.

- **Bytecode Generation**: For bytecode transformations, JavaTransformer uses ASM to generate and modify bytecode, providing low-level control over the JVM instructions.

- **Source Code Generation**: For source code transformations, JavaTransformer uses JavaParser to generate and modify Java source code, preserving formatting and comments where possible.

## Core Components and Their Interactions

### JavaTransformer

The `JavaTransformer` class (`src/main/java/dev/minco/javatransformer/api/JavaTransformer.java`) serves as the main entry point for the transformation process. It orchestrates the loading, transformation, and saving of Java classes and source files.

Key implementation details:
- Maintains lists of general and targeted transformers
- Uses a `ClassPath` object for type resolution
- Implements separate logic for handling JAR files and folders
- Delegates actual transformation to `ClassInfo` implementations

### ClassInfo Hierarchy

The `ClassInfo` interface provides a unified abstraction for both bytecode and source code representations of Java classes.

#### ByteCodeInfo

`ByteCodeInfo` represents classes loaded from bytecode (.class files).

Implementation details:
- Uses ASM's `ClassNode` as the underlying representation
- Lazily parses bytecode using `AsmUtil.getClassNode()`
- Implements `MethodInfo` and `FieldInfo` using ASM's `MethodNode` and `FieldNode`

#### SourceInfo

`SourceInfo` represents classes loaded from source code (.java files).

Implementation details:
- Uses JavaParser's `TypeDeclaration` as the underlying representation
- Parses source code using JavaParser
- Implements `MethodInfo` and `FieldInfo` using JavaParser's `MethodDeclaration` and `FieldDeclaration`

### Transformation Process

1. **Loading**: 
   - JAR files are processed using `ZipInputStream`
   - Folders are traversed using `Files.walkFileTree()`
   - Files are categorized as bytecode, source code, or other based on file extension

2. **Parsing**:
   - Bytecode: ASM's `ClassReader` is used to create a `ClassNode`
   - Source code: JavaParser is used to create a `CompilationUnit` and extract `TypeDeclaration`s

3. **Abstraction**:
   - `ByteCodeInfo` or `SourceInfo` objects are created, wrapping the parsed data
   - These objects implement the `ClassInfo` interface, providing a unified API

4. **Transformation**:
   - General transformers are applied to all `ClassInfo` objects
   - Targeted transformers are applied to specific classes based on name matching
   - Transformers modify the `ClassInfo`, `MethodInfo`, and `FieldInfo` objects

5. **Code Generation**:
   - Bytecode: Modified `ClassNode` objects are written using ASM's `ClassWriter`
   - Source code: Modified AST nodes are converted back to source code using JavaParser's pretty-printing capabilities

6. **Saving**:
   - Transformed classes are written back to JAR files or folders
   - The original file structure is preserved

## CodeFragment System

The `CodeFragment` interface represents a piece of code that can be inserted into a method, supporting both bytecode and source code formats.

### ByteCodeFragment

Represents bytecode instructions.

Implementation details:
- Stores a list of ASM `AbstractInsnNode` objects
- Can be created from raw bytecode instructions or higher-level representations

### SourceCodeFragment

Represents source code snippets.

Implementation details:
- Stores JavaParser `Statement` or `Expression` objects
- Can be created from string literals or pre-parsed AST nodes

### CodeFragmentGenerator

The `CodeFragmentGenerator` interface provides methods to generate `CodeFragment` objects from various input formats.

#### AsmCodeFragmentGenerator

Generates `ByteCodeFragment` objects.

Implementation details:
- Uses ASM to parse and generate bytecode instructions
- Handles conversion between high-level code representations and low-level bytecode

#### SourceCodeFragmentGenerator

Generates `SourceCodeFragment` objects.

Implementation details:
- Uses JavaParser to parse source code snippets into AST nodes
- Handles conversion between string literals and structured AST representations

## Type Resolution and Context

### ResolutionContext

The `ResolutionContext` class provides context for resolving types and members within a class or method.

Implementation details:
- Manages imports, type parameters, and scoping information
- Uses the `ClassPath` to resolve external types
- Implements separate resolution logic for bytecode and source code contexts

### ClassPath

The `ClassPath` class represents the classpath used for resolving types during transformation.

Implementation details:
- Supports both file system and in-memory class loading
- Caches resolved classes for performance
- Handles resolution of array types and primitives

## Utility Classes

### AsmUtil

Provides utility methods for working with ASM.

Key functionalities:
- Reading and writing class files
- Converting between ASM and JavaTransformer type representations
- Generating bytecode for common operations (e.g., method calls, field access)

### JavaParserUtil

Provides utility methods for working with JavaParser.

Key functionalities:
- Parsing and manipulating Java source code
- Converting between JavaParser and JavaTransformer type representations
- Generating AST nodes for common operations

### JVMUtil

Provides utility methods for working with JVM-related concepts.

Key functionalities:
- Converting between class names and file names
- Handling JVM type descriptors and signatures

## Class Loading and Resolution

JavaTransformer employs a sophisticated class loading and resolution system to support its transformation capabilities:

1. **ClassPath Hierarchy**: The `ClassPath` interface and its implementations (`FileClassPath`) provide a flexible way to represent and search for classes across multiple sources, including the system classpath, JARs, and directories.

2. **Lazy Initialization**: The `FileClassPath` uses lazy initialization to load classes only when needed, improving performance for large classpaths.

3. **Multiple Formats**: JavaTransformer can load and process both `.class` (bytecode) and `.java` (source code) files, providing a unified `ClassInfo` representation for both.

4. **Resolution Context**: The `ResolutionContext` class plays a crucial role in resolving types, methods, and fields within the context of a specific class or method. This context-aware resolution is essential for accurate transformations, especially when dealing with complex type hierarchies or generic types.

5. **Type Parameter Resolution**: JavaTransformer includes logic for resolving generic type parameters, as seen in the `Expressions` class, allowing it to handle complex generic scenarios in both bytecode and source code transformations.

These class loading and resolution capabilities enable JavaTransformer to perform accurate and context-aware transformations across a wide range of Java code structures and formats.

## Forward Compatibility and JDK Version Support

JavaTransformer aims to maintain compatibility with future JDK versions through several mechanisms:

1. **Fallback to Reflection**: As noted in the `ClassPaths` class, there's a planned feature to fall back to reflection if ASM cannot load JDK classes:

   ```java
   // TODO: self-test, if we can't load JDK classes with current asm version fall back to reflection
   ```

   This fallback mechanism would allow JavaTransformer to understand the structure of JDK classes for use in `ResolutionContext`, even if it cannot modify them directly. While this limits the ability to transform these classes, it ensures that type resolution and other critical functionalities remain operational.

2. **Modular JDK Support**: The `SystemClassPath` implementation already includes support for the modular JDK structure introduced in Java 9:

   ```java
   val fs = FileSystems.getFileSystem(URI.create("jrt:/"));
   return new FileClassPath(null, Collections.singletonList(fs.getPath("modules/java.base/")));
   ```

   This allows JavaTransformer to work with both traditional and modular JDK structures.

3. **Abstract Representations**: By using abstract representations like `ClassInfo`, `MethodInfo`, and `FieldInfo`, JavaTransformer can potentially adapt to changes in underlying class file formats without requiring extensive modifications to the core API.

While these mechanisms provide a degree of forward compatibility, users should be aware that full support for new JDK features may require updates to JavaTransformer, particularly for transformations involving new language constructs or bytecode instructions.

## Extension Points for Contributors

To extend or customize JavaTransformer, consider the following areas:

1. **Custom Transformers**: Implement the `Transformer` interface to create new transformation logic.

2. **New CodeFragment Types**: Extend `CodeFragment` to support new ways of representing and inserting code.

3. **Additional File Formats**: Extend the loading and saving logic in `JavaTransformer` to support new file formats beyond JAR and folders.

4. **Enhanced Type Resolution**: Extend `ResolutionContext` or `ClassPath` to improve type resolution capabilities, especially for complex scenarios involving generics or lambda expressions.

5. **Optimization Passes**: Implement transformers that perform bytecode or source code optimizations.

6. **Integration with Other Tools**: Create adapters or wrappers to integrate JavaTransformer with other bytecode or source code manipulation tools.

By understanding these components and their interactions, developers can effectively use, extend, and contribute to the JavaTransformer project.
