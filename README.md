# terminal-emulator
A terminal emulator java library used in [Termux](https://termux.com/). This library contains the abstract terminal emulator - to use it clients needs to wire up the terminal emulator with processes and display it in a GUI (if so desired).

The main entry point is the [TerminalEmulator](src/main/java/com/termux/terminal/TerminalEmulator.java) class which represents a terminal emulator. Clients create a [TerminalClient](src/main/java/com/termux/terminal/TerminalClient.java) to be notified by callbacks from the terminal emulator (such as input to be written to the foreground process) and manipulates it when key presses, mouse events and resize events are to be sent to the terminal.

See [the tests](src/test/java/com/termux/terminal) for example usage and behaviour.

# Building
The project is built with a standard Gradle setup and can be opened in Eclipse (with Buildship installed) or IntelliJ without any configuration.
