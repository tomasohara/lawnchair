// Interface for the helper that Shizuku runs with shell privileges (see ShellService.java).
// Created with the help of Claude Code using model claude-fable-5-2.
package app.lawnchair.homeswitcher;

interface IShellService {
    // Reserved by Shizuku for shutting the helper down (n.b., the transaction code is fixed)
    void destroy() = 16777114;

    // Makes COMPONENT the default home app for USER_ID, returning the command output
    String setHomeActivity(String component, int userId) = 1;
}
