# AdvancedSlidesAgent Usage Guide

The `AdvancedSlidesAgent` acts like NotebookLM to analyze source material and generate Obsidian Advanced Slides (Reveal.js) markdown.

## CLI Command

You can invoke the agent using the `slides` command in the Spring Shell.

### Syntax
```bash
slides --workspace-id <id> --source <material-or-path> --instructions <instructions> [-p] [-r]
```

### Parameters
- `--workspace-id`: Unique identifier for the workspace. This will be used to create the output directory under `output/AdvancedSlidesAgent/<slug>/`.
- `--source`: Raw source text or a path to a `.txt` or `.md` file containing the source material.
- `--instructions`: User's specific instructions for slide generation (e.g., "Create a 5-slide deck focusing on the architecture").
- `-p`, `--show-prompts`: Optional flag to show the prompts sent to the LLM.
- `-r`, `--show-responses`: Optional flag to show the raw LLM responses.

### Example
```bash
slides --workspace-id project-alpha --source "This is a long text about project alpha..." --instructions "Generate 3 slides for a project kickoff."
```
Or using a file:
```bash
slides --workspace-id project-beta --source guides/few-shots/adv-slides-few-shot.md --instructions "Summarize the key layout engineering patterns into 4 slides."
```

## Output

The agent generates:
1.  A slide-by-slide structure plan (internal).
2.  The final Obsidian Advanced Slides markdown.

The final markdown is:
1.  Printed to the console.
2.  Saved to `output/AdvancedSlidesAgent/<slug>/export/slides_<timestamp>.md`.

## Features
- **Intelligent Structuring**: Analyzes source material to create a logical progression.
- **Obsidian Advanced Slides Integration**: Uses the `consult` theme and supports grid layouts, Font Awesome icons, and Mermaid diagrams.
- **Strict Adherence to Guidelines**: Follows professional slide deck formatting rules defined in `guides/few-shots/adv-slides-few-shot.md`.
