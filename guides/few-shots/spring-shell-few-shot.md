# Spring Shell Few-Shot (v3.4.0): Comprehensive Guide

This guide provides structured examples for Spring Shell v3.4.0, optimized for LLM context.

# Chapter 1: Basic Annotation-Based Command

## 1.1. Dependency Setup (pom.xml)
Core dependency for Spring Shell 3.4.0 standard annotations.

```xml
<properties>
    <java.version>21</java.version>
    <spring-shell.version>3.4.0</spring-shell.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.shell</groupId>
        <artifactId>spring-shell-starter</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.shell</groupId>
            <artifactId>spring-shell-dependencies</artifactId>
            <version>${spring-shell.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 1.2. Task: Implementing the 'hello' Command
Create a command that takes a name and prints a greeting.
- Command: `hello`
- Option: `--name` (Default: "World")

## 1.3. Solution (Annotation-Based)
```java
package com.example.shell;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class GreetingCommands {

    /**
     * @ShellMethod: Registers a method as a shell command.
     * @ShellOption: Defines command options.
     */
    @ShellMethod(value = "Receive a name and print a greeting.", key = "hello")
    public String hello(
            @ShellOption(defaultValue = "World", help = "The name to greet") String name) {
        return "Hello " + name + "!";
    }
}
```

## 1.4. Execution Example
```bash
# Non-interactive mode execution
mvn spring-boot:run -Dspring-boot.run.arguments="hello --name user"

# Output:
# Hello user!
```

# Chapter 2: Command Registration & Grouping

Covers Annotation vs. Programmatic registration and grouping for better help output.

## 2.1. Annotation-Based Registration with Grouping
Register a class as a bean using `@ShellComponent` and methods using `@ShellMethod`.

```java
package com.example.shell;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class UserCommands {

    @ShellMethod(value = "Perform user login.", key = "login", group = "Account Management")
    public String login(
            @ShellOption(help = "User name") String user) {
        return "Logged in as " + user;
    }
}
```

## 2.2. Programmatic Registration (CommandRegistration)
Explicit registration via `@Bean`. Useful for dynamic commands or granular control.

```java
package com.example.shell;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.command.CommandRegistration;

@Configuration
public class CommandConfig {

    @Bean
    public CommandRegistration pingCommand() {
        return CommandRegistration.builder()
                .command("ping")
                .group("System Info")
                .description("Check server response.")
                .withTarget()
                    .function(ctx -> "Pong!")
                    .and()
                .build();
    }
}
```

## 2.3. Help Screen Example
Grouping categorizes commands in the `help` output:

```text
Account Management
       login: Perform user login.

System Info
       ping: Check server response.
```

# Chapter 3: Syntax & Validation

Covers Positional Arguments, Named Options, and Bean Validation.

## 3.1. Dependency Setup (pom.xml)
Included by default in `spring-shell-starter`.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

## 3.2. Task 1: Positional Arguments
Mapping values without explicit option names.

```java
package com.example.shell;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class ValidationCommands {

    @ShellMethod(value = "Greet using a positional argument.", key = "greet")
    public String greet(
            @ShellOption(defaultValue = "Friend") String name) {
        return "Hello, " + name + "!";
    }
}
```
**Execution:** `greet Gemini` -> `Hello, Gemini!`

## 3.3. Task 2: Named Options & Validation
Defining aliases (`-p`, `--password`) and using `@Size` constraints.

```java
package com.example.shell;

import jakarta.validation.constraints.Size;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class ValidationCommands {

    @ShellMethod(value = "Change the password.", key = "change-password")
    public String changePassword(
            @ShellOption(value = {"--password", "-p"}, help = "New password (min 8 chars)") 
            @Size(min = 8) String password) {
        return "Password changed.";
    }
}
```

# Chapter 4: Command Availability

Dynamically control command availability based on application state.

## 4.1. Task: 'download' Availability Based on Connection
Prevent `download` before `connect` is executed.

## 4.2. Solution (Annotation-Based)
```java
package com.example.shell;

import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;

@ShellComponent
public class AvailabilityCommands {

    private boolean connected = false;

    @ShellMethod(value = "Connect to the server.", key = "connect")
    public String connect() {
        this.connected = true;
        return "Connected to server.";
    }

    @ShellMethod(value = "Disconnect from the server.", key = "disconnect")
    public String disconnect() {
        this.connected = false;
        return "Disconnected.";
    }

    @ShellMethod(value = "Download a file.", key = "download")
    @ShellMethodAvailability("downloadAvailability")
    public String download() {
        return "Downloaded 100MB.";
    }

    public Availability downloadAvailability() {
        return connected 
                ? Availability.available() 
                : Availability.unavailable("you are not connected");
    }
}
```

## 4.3. Key Points
- In `Availability.unavailable("reason")`, it is conventional to start the reason with a lowercase letter and omit the period so it follows "because" naturally.
- Availability methods must be `public` and return the `Availability` type.

# Chapter 5: Command Completion

Implementing auto-completion for option values.

## 5.1. Enum-Based Completion (EnumValueProvider)
Suggests Enum constants as candidates.

```java
package com.example.shell;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.shell.standard.EnumValueProvider;

@ShellComponent
public class CompletionCommands {

    @ShellMethod(value = "Greet based on gender.", key = "greet-gender")
    public String greetGender(
            @ShellOption(valueProvider = EnumValueProvider.class) Gender gender) {
        return "Gender: " + gender;
    }
}
```

## 5.2. Custom Completion (ValueProvider)
Implement `ValueProvider` for custom lists.

### 5.2.1. Step 1: Implement ValueProvider
```java
package com.example.shell;

import java.util.List;
import org.springframework.shell.CompletionContext;
import org.springframework.shell.CompletionProposal;
import org.springframework.shell.standard.ValueProvider;
import org.springframework.stereotype.Component;

@Component
public class NameValueProvider implements ValueProvider {
    @Override
    public List<CompletionProposal> complete(CompletionContext completionContext) {
        return List.of("Alice", "Bob", "Charlie").stream()
                .map(CompletionProposal::new).toList();
    }
}
```

### 5.2.2. Step 2: Connect to the Command
```java
@ShellMethod(key = "greet-name")
public String greetName(
        @ShellOption(valueProvider = NameValueProvider.class) String name) {
    return "Hello " + name;
}
```

## 5.3. Key Points
- In the Spring Shell 3.4.0 standard model, **`EnumValueProvider.class`** and the **`ValueProvider`** interface are used.
- Be careful not to confuse it with the `EnumCompletionProvider` from version 4.0.x (which does not exist in 3.4.0).
- For auto-completion suggestions, it is best to press `Tab` after entering the option name (e.g., `--name`).

# Chapter 6: Input, Output & Exception Handling

## 6.1. Interactive Input (StringInput)
Used for sensitive data or step-by-step input during execution.

```java
package com.example.shell;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.shell.component.StringInput;
import org.springframework.shell.component.StringInput.StringInputContext;
import org.springframework.shell.standard.AbstractShellComponent;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.style.TemplateExecutor;

@ShellComponent
public class ExceptionCommands extends AbstractShellComponent {

    @Autowired private ResourceLoader resourceLoader;
    @Autowired private TemplateExecutor templateExecutor;

    @ShellMethod(value = "Create a new user.", key = "create-user")
    public void createUser() {
        StringInput nameInput = new StringInput(getTerminal(), "Username: ", null);
        // Mandatory setup for UI components in v3.4.0
        nameInput.setResourceLoader(resourceLoader);
        nameInput.setTemplateExecutor(templateExecutor);
        
        StringInputContext nameContext = nameInput.run(StringInputContext.empty());
        String name = nameContext.getResultValue();

        StringInput passwordInput = new StringInput(getTerminal(), "Password: ", null);
        passwordInput.setResourceLoader(resourceLoader);
        passwordInput.setTemplateExecutor(templateExecutor);
        passwordInput.setMaskCharacter('*');
        
        String password = passwordInput.run(StringInputContext.empty()).getResultValue();

        getTerminal().writer().println("\nUser [" + name + "] created successfully!");
        getTerminal().writer().flush();
    }
}
```

## 6.2. Global Exception Handling (CommandExceptionResolver)
Intercept exceptions to provide specific messages and exit codes.

```java
package com.example.shell;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.command.CommandExceptionResolver;
import org.springframework.shell.command.CommandHandlingResult;

@Configuration
public class ExceptionConfig {

    @Bean
    public CommandExceptionResolver secretExceptionResolver() {
        return ex -> {
            if (ex instanceof SecretException) {
                return CommandHandlingResult.of("Unauthorized Access!\n", 77);
            }
            return null; // Delegate to default handler
        };
    }
}
```

# Chapter 7: Built-In Commands & Building

- `help`, `clear`, `exit`, `quit`.
- `script --file commands.txt`: Automate multiple commands.


**Example of commands.txt:**
```text
hello --name Alice
help
exit
```

# Chapter 8: UI Components (Direct vs Flow)

Spring Shell provides various interactive UI components to assist user input. Simple inputs call **individual components** directly, while complex multi-step inputs use **ComponentFlow**.

## 8.1. Major UI Component Types
- `StringInput`: Text input (password masking possible)
- `PathInput`: Direct path input
- `ConfirmationInput`: Y/N confirmation question
- `SingleItemSelector`: Single item selection (dropdown)
- `MultiItemSelector`: Multiple item selection (checkbox)

## 8.2. Individual Components (Manual Injection. Not recommended)
Requires manual setup of `ResourceLoader` and `TemplateExecutor`.

```java
package com.example.shell;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.shell.component.StringInput;
import org.springframework.shell.style.TemplateExecutor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.AbstractShellComponent;

@ShellComponent
public class DirectComponentCommand extends AbstractShellComponent {

    @Autowired private ResourceLoader resourceLoader;
    @Autowired private TemplateExecutor templateExecutor;

    @ShellMethod(value = "Direct call of individual components.", key = "direct-ui")
    public void runDirect() {
        StringInput pwdInput = new StringInput(getTerminal(), "Password: ", null);
        pwdInput.setResourceLoader(resourceLoader);
        pwdInput.setTemplateExecutor(templateExecutor);
        pwdInput.setMaskCharacter('*');
        
        String password = pwdInput.run(StringInput.StringInputContext.empty()).getResultValue();
        System.out.println("Password entered: " + (password != null));
    }
}
```

## 8.3. ComponentFlow (Recommended)
Handles dependencies automatically and supports chaining.

```java
package com.example.shell;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.component.flow.ComponentFlow;
import org.springframework.shell.component.flow.SelectItem;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import java.util.Arrays;

@ShellComponent
public class FlowComponentCommand {

    @Autowired private ComponentFlow.Builder componentFlowBuilder;

    @ShellMethod(value = "Test ComponentFlow chaining.", key = "flow-ui")
    public void runFlow() {
        ComponentFlow flow = componentFlowBuilder.clone().reset()
                .withStringInput("name")
                    .name("Please enter your name")
                    .defaultValue("Guest")
                    .and()
                .withSingleItemSelector("mode")
                    .name("Select execution mode")
                    .selectItems(Arrays.asList(
                            SelectItem.of("Fast", "FAST"),
                            SelectItem.of("Safe", "SAFE")
                    ))
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult result = flow.run();
        System.out.println("Result -> Name: " + result.getContext().get("name"));
    }
}
```

# Chapter 9: Terminal UI (TUI)
Experimental in v4.0.1+. Not recommended in v3.4.0.

# Chapter 10: Customization (Prompt, Theme, MOTD)

## 10.1. Custom Prompt (PromptProvider)
```java
@Configuration
public class CustomPromptConfig {
    @Bean
    public PromptProvider myPromptProvider() {
        return () -> new AttributedString("my-shell:>", 
                AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
    }
}
```

## 10.2. Custom Theme (Styles & Figures)
Must inherit from `ThemeSettings`.

```java
@Configuration
public class ThemeConfig {
    static class MyStyleSettings extends StyleSettings {
        @Override public String highlight() { return "fg:cyan"; }
    }
    static class MyFigureSettings extends FigureSettings {
        @Override public String error() { return "[X]"; }
    }
    static class MyThemeSettings extends ThemeSettings {
        public MyThemeSettings() { super(new MyStyleSettings(), new MyFigureSettings()); }
    }

    @Bean
    public Theme coolTheme() {
        return new Theme() {
            @Override public String getName() { return "cool-theme"; }
            @Override public ThemeSettings getSettings() { return new MyThemeSettings(); }
        };
    }
}
```

**Application Property setting (`application.yml` or `.properties`):**
```yaml
spring:
  shell:
    theme:
      name: cool-theme
```

## 10.3. MOTD (Message of the Day)
```java
@Configuration
public class ShellBannerConfig {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ApplicationRunner motdRunner(Terminal terminal) {
        return args -> {
            terminal.writer().println("========================================");
            terminal.writer().println("Welcome to My Professional Shell!");
            terminal.writer().println("========================================");
            terminal.writer().flush();
        };
    }
}
```

# Chapter 11: Execution & Testing

## 11.1. Execution Mode (application.properties)
```properties
spring.shell.interactive.enabled=false
spring.shell.debug.enabled=true
```

## 11.2. Shell Integration Test (@ShellTest)
```java
@ShellTest
class ShellApplicationTest {
    @Autowired ShellTestClient client;

    @Test
    void testHelloCommand() throws Exception {
        client.nonInterative("hello", "--name", "Gemini").run();
        
        // Use Awaitility for asynchronous rendering
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            ShellScreen screen = client.screen();
            ShellAssertions.assertThat(screen).containsText("Hello Gemini!");
        });
    }
}
```

# Chapter 12: Execution & Logging

## 12.1. Clean Environment (application.properties)
```properties
spring.main.banner-mode=off
spring.main.log-startup-info=false
logging.threshold.console=OFF
logging.pattern.console=
logging.file.name=.shell.log
logging.level.org.springframework.shell=DEBUG
logging.level.root=INFO
```

## 12.2. Execution Methods
- Dev: `mvn spring-boot:run`
- Prod: `mvn clean package -DskipTests` -> `java -jar target/app.jar`
