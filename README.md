# Lenses On Remote

## Motivation
This project demonstrates the implementation of code lenses in IntelliJ IDEA, specifically focusing on the challenges and differences between local and remote development environments (split mode).

## Description
This is a proof-of-concept (POC) project that implements code lenses using IntelliJ's Code Vision and Code Vision Providers. The plugin displays mock action lenses above the current caret position. The actual behavior of these actions is not implemented as the focus is on the lens display mechanism itself.

## Expected Behavior
When running, the plugin should:
1. Track the caret position in the editor
2. Display mock action lenses in the line above the current caret position
3. Update lens positions dynamically as the caret moves

## Observed Behavior in Remote Development
When running in remote development (split mode):
- Position updates are delayed or missing entirely
- Lens display feels unresponsive and buggy

## Running the Plugin

### Regular Run
```bash
./gradlew customRunIde
```

### Remote Development (Split Mode)
```bash
./gradlew customRunIde -PsplitMode=true
```
