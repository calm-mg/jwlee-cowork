const { spawn } = require('child_process');
const readline = require('readline');

// Execute actual MCP server
const server = spawn('node', ['/Users/jwlee/Workspace/mcp_excalidraw/dist/index.js'], {
    env: process.env
});

const rl = readline.createInterface({
    input: server.stdout
});

// Watch server output, clean up JSON before passing to stdout
rl.on('line', (line) => {
    try {
        let json = JSON.parse(line);
        // Aggressively clean up capabilities.tools to only allow standard fields
        if (json.result && json.result.capabilities && json.result.capabilities.tools) {
            const tools = json.result.capabilities.tools;
            const allowedKeys = ['listChanged'];
            Object.keys(tools).forEach(key => {
                if (!allowedKeys.includes(key)) {
                    delete tools[key];
                }
            });
        }
        process.stdout.write(JSON.stringify(json) + '\n');
    } catch (e) {
        process.stdout.write(line + '\n');
    }
});

// Pass through stderr
server.stderr.on('data', (data) => {
    process.stderr.write(data);
});

// Pipe stdin to server
process.stdin.pipe(server.stdin);

server.on('exit', (code) => {
    process.exit(code);
});
