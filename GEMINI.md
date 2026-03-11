# Minobot

This application is used to enhance the quality of life when playing the game named **Dofus Retro**.
It provides a silent process running in the background listening to all kind of events or hotkey presses and acts accordingly.

## Coding rules

Every line of code should be written in English.

Comments are in French.

All Python best practices must be respected. See the `AGENTS.md` file for all guidelines.

When adding code, make sure the documentation and all the `GEMINI.md` guideline files are up-to-date.
There are several `GEMINI.md` files across the application, each of them focusing on their directory.

## Architecture

```
.
├── build.py
├── main.py
├── app
│   ├── Entry point of the application, configuration & logger are here.
├── core
│   ├── Anything related to technical Windows mechanics.
└── features
    └── Features are placed here.
```