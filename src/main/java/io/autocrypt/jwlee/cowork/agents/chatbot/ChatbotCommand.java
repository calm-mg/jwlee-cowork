package io.autocrypt.jwlee.cowork.agents.chatbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import com.embabel.agent.api.channel.MessageOutputChannelEvent;
import com.embabel.chat.ChatSession;
import com.embabel.chat.Chatbot;
import com.embabel.chat.UserMessage;

import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import io.autocrypt.jwlee.cowork.core.ui.TerminalSpinner;

@ShellComponent
public class ChatbotCommand {

    private final Chatbot chatbot;
    private final Terminal terminal;
    private final LocalRagTools localRagTools;
    private final TerminalSpinner spinner;
    private ChatSession currentSession;

    public ChatbotCommand(Chatbot chatbot, Terminal terminal, LocalRagTools localRagTools) {
        this.chatbot = chatbot;
        this.terminal = terminal;
        this.localRagTools = localRagTools;
        this.spinner = new TerminalSpinner(terminal);
    }

    @ShellMethod(value = "Enter interactive chat mode.", key = {"ask", "chat"})
    public void chatMode() {
        // Ensure 'chatbot' in-memory instance is fresh
        localRagTools.closeMemoryInstance("chatbot");

        try {
            // Build LineReader with persistent history file support
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .variable(LineReader.HISTORY_FILE, Paths.get(".chatbot_history"))
                    .build();

            // Styled prompt: Bold Cyan "> "
            String prompt = new AttributedString("> ", 
                    AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold()).toAnsi();
            
            while (true) {
                String line;
                try {
                    line = reader.readLine(prompt);
                    if (line == null || line.trim().isEmpty()) continue;
                    if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) break;
                    
                    ask(line);
                } catch (UserInterruptException e) {
                    break;
                } catch (EndOfFileException e) {
                    break;
                }
            }
        } finally {
            // Clear 'chatbot' in-memory instance after session ends
            localRagTools.closeMemoryInstance("chatbot");
            reset();
        }
    }

    /**
     * Internal helper to process a message via the chatbot.
     */
    private void ask(String message) {
        if (currentSession == null) {
            // Create a session that prints AI responses to the terminal
            currentSession = chatbot.createSession(null, event -> {
                if (event instanceof MessageOutputChannelEvent me) {
                    spinner.stop();
                    // Styled prefix: Bold Yellow "✦ "
                    String prefix = new AttributedString("✦ ", 
                            AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold()).toAnsi();
                    terminal.writer().println(prefix + me.getMessage().getContent());
                    terminal.writer().println();
                    terminal.writer().flush();
                }
            }, null, "main-orchestrator-session");
        }

        spinner.start("Thinking...");
        currentSession.onUserMessage(new UserMessage(message));
    }

    /**
     * Internal helper to reset the current chat session.
     */
    private void reset() {
        this.currentSession = null;
    }
}
