# Nats Graalvm Example

Demonstrating the integration of NATS with GraalVM for creating efficient native executables, showcasing optimal
configurations and techniques for leveraging NATS in high-performance, low-footprint applications.

### Requirements

* `Graalvm` >= 21.0.0 in `$JAVA_HOME` environment variable

### Native Builds

* Maven build native executable _(see [pom file](pom.xml))_: `./mvnw clean package -Pnative`
* CLI minimal _(
  replace `your-application-classpath`)_:

```shell
    native-image \
    --initialize-at-run-time=io.nats.client.support.RandomUtils \
    --initialize-at-run-time=java.security.SecureRandom \
    -H:Name=native-executable \
    -cp your-application-classpath berlin.yuna.nativeexample.Main
```

* CLI strict _(
  replace `your-application-classpath`)_:

```shell
    native-image \
    --no-fallback \
    --no-server \
    --initialize-at-build-time \
    --initialize-at-run-time=io.nats.client.support.RandomUtils \
    --initialize-at-run-time=java.security.SecureRandom \
    -H:Name=native-executable \
    -cp your-application-classpath berlin.yuna.nativeexample.Main
```

### GraalVM parameter explanation

| Parameter                                                     | Description                                                                                                                                                                                                                                                                                                                                                                                                                                     |
|---------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `--no-fallback`                                               | Disables the fallback feature, ensuring that the native image doesn't include the JVM. This results in a smaller, more efficient executable but requires all code to be fully native-image compatible.                                                                                                                                                                                                                                          |
| `--initialize-at-build-time`                                  | Instructs GraalVM to initialize specified classes at build time rather than at runtime. This can improve startup time but requires careful management of class initialization to avoid errors.                                                                                                                                                                                                                                                  |
| `--initialize-at-run-time=io.nats.client.support.RandomUtils` | Specifies that the `io.nats.client.support.RandomUtils` class should be initialized at runtime. This is needed because this class likely uses or initializes `Random` or `SecureRandom` instances in static fields. Static initialization at build time would cause these instances to have a fixed seed and not behave as expected at runtime, reducing randomness and potentially affecting the security or functionality of the application. |
| `--initialize-at-run-time=java.security.SecureRandom`         | Indicates that the `java.security.SecureRandom` class should be initialized at runtime. `SecureRandom` is used for cryptographic operations and relies on entropy sources that are only available at runtime. Initializing it at build time would result in a lack of proper randomness as the entropy sources would not be properly utilized, potentially compromising security.                                                               |
| `-H:Name=native-executable`                                   | Sets the name of the generated executable file.                                                                                                                                                                                                                                                                                                                                                                                                 |
| `-cp "<class-path>"`                                          | Specifies the classpath, which includes paths to all classes and libraries that your application depends on. Separate multiple paths with `:` on Unix-like systems or `;` on Windows.                                                                                                                                                                                                                                                           |
| `<main-class>`                                                | The fully qualified name of your main class. This is the entry point of your application.                                                                                                                                                                                                                                                                                                                                                       |

### Application description

This example uses [jNats](https://github.com/nats-io/nats.java) client to connect to
the [Nats Server](https://github.com/YunaBraska/nats-server) which is started within this project.

