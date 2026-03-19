package io.autocrypt.jwlee.cowork.agents.chatbot;

import org.jline.terminal.Terminal;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatbotSpinner {
    private static final String[] FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private final Terminal terminal;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    public ChatbotSpinner(Terminal terminal) {
        this.terminal = terminal;
    }

    public void start(String message) {
        if (running.get()) return;
        running.set(true);
        thread = new Thread(() -> {
            int i = 0;
            try {
                while (running.get()) {
                    terminal.writer().print("\r" + FRAMES[i % FRAMES.length] + " " + message);
                    terminal.writer().flush();
                    i++;
                    Thread.sleep(80);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running.set(false);
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Clear the spinner line
        terminal.writer().print("\r" + " ".repeat(40) + "\r");
        terminal.writer().flush();
    }
}
