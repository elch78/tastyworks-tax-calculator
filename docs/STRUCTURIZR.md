# Structurizr Lite - C4 Architecture Diagrams

This project uses Structurizr Lite to create and maintain component-level architecture diagrams.

## What is Structurizr?

Structurizr is a tool for creating C4 model diagrams using a text-based DSL (Domain Specific Language). For this CLI application, we focus on component-level diagrams that show how the internal parts of the application work together.

## Getting Started

### Automatic Startup

Structurizr Lite starts automatically when you rebuild or restart your dev container and runs on port 8080.

Access it at: **http://localhost:8080**

### Manual Control

If you need to manually start/stop Structurizr:

```bash
# Start Structurizr
bash .devcontainer/start-structurizr.sh

# Stop Structurizr (find and kill the process)
pkill -f structurizr-lite.war

# Check logs
tail -f /tmp/structurizr.log
```

## Available Diagrams

The workspace includes three component views:

1. **Components** - Complete view showing all components and their relationships
2. **CoreFlow** - Simplified view showing the main processing flow
3. **PortfolioDetails** - Detailed view of portfolio position management

### Color Coding

Components are color-coded by their role:
- **Dark Blue** (Entry Point) - ApplicationRunner
- **Light Blue** (Core Domain) - Portfolio, FiscalYear, Position classes
- **Lighter Blue** (Service) - CurrencyExchange, SnapshotService
- **Pale Blue** (Repository) - Data access components
- **Gray** (Input) - CSV readers
- **Green** (Event) - Spring application events

## Editing the Architecture

### Option 1: Web UI

1. Open http://localhost:8080 in your browser
2. Click the "DSL" button in the toolbar to edit the workspace
3. Make your changes and click "Save"
4. Changes are automatically written to `docs/workspace.dsl`

### Option 2: Edit DSL File Directly

1. Edit `docs/workspace.dsl` in your IDE
2. Save the file
3. Refresh your browser - Structurizr will reload automatically

## Workspace Structure

The `docs/workspace.dsl` file contains:

- **Model**: Defines components and their relationships
  - Components represent classes/modules in the codebase
  - Relationships show dependencies and event flows
  - Tags categorize components by role

- **Views**: Defines which diagrams to generate
  - Full component view with all elements
  - Filtered views showing specific aspects
  - Auto-layout for automatic positioning

- **Styles**: Customizes colors, shapes, and visual appearance
  - Color coding by component role
  - Different shapes for events vs components

## Tips

- Use `#` for comments in the DSL
- Component names should match actual class names for clarity
- Use tags to group related components and apply consistent styling
- Multiple views let you show different levels of detail
- Export diagrams as PNG/SVG from the web UI for documentation

## Learn More

- [Structurizr DSL Documentation](https://github.com/structurizr/dsl)
- [C4 Model](https://c4model.com/)
- [Structurizr Lite](https://structurizr.com/help/lite)
