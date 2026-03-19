package io.autocrypt.jwlee.cowork.core.ui;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.shell.jline.PromptProvider;

@Configuration
public class JwleeUiConfig {

    @Value("${project.version:0.1.0-SNAPSHOT}")
    private String version;

    @Value("${cli.ui.title:JWLEE CLI}")
    private String title;

    @Value("${cli.ui.help-text:도움말은 help 입력}")
    private String helpText;

    @Value("${cli.ui.greeting:오늘도 좋은 하루 되세요.}")
    private String greeting;

    /**
     * Customizes the shell prompt to be simple "> "
     */
    @Bean
    public PromptProvider jwleePromptProvider() {
        return () -> new AttributedString("> ", 
                AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
    }

    /**
     * Prints the JWLEE CLI banner on startup.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ApplicationRunner jwleeBannerRunner(Terminal terminal) {
        return args -> {
            var writer = terminal.writer();
            writer.println(); // Add an empty line before the logo

            // ANSI Styles
            AttributedStyle logoStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA).bold();
            AttributedStyle infoStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE);

            // 1. ASCII Logo & Title
            writer.println(new AttributedString("  ▝▜▄     ", logoStyle).toAnsi() + title + " v" + version);
            writer.println(new AttributedString("    ▝▜▄   ", logoStyle).toAnsi());
            writer.println(new AttributedString("   ▗▟▀    ", logoStyle).toAnsi() + helpText);
            writer.println(new AttributedString("  ▝▀      ", logoStyle).toAnsi());
            writer.println();

            // 2. Greeting
            writer.println(greeting);
            
            // 3. Horizontal Line (hline)
            drawHorizontalLine(terminal);
            
            writer.flush();
        };
    }

    private void drawHorizontalLine(Terminal terminal) {
        int width = terminal.getWidth();
        // Fallback for non-interactive terminals or IDE terminals
        if (width <= 0) width = 80;
        
        String line = "─".repeat(width);
        terminal.writer().println(new AttributedString(line, AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE)).toAnsi());
    }
}
