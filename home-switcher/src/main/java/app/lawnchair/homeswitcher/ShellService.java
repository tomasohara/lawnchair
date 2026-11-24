/*
 * Helper for the home switcher that runs with shell privileges.
 *
 * Shizuku (https://shizuku.rikka.app) starts this class in a separate process owned by the
 * shell user, which is the same identity that "adb shell" has. That identity is allowed to
 * change the default home app, unlike a regular app.
 *
 * Created with the help of Claude Code using model claude-fable-5-2.
 */

package app.lawnchair.homeswitcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/** Shell-side implementation of {@link IShellService}. */
public class ShellService extends IShellService.Stub {

    /** Constructor used by Shizuku (n.b., must be public and without arguments). */
    public ShellService() {
    }

    @Override
    public void destroy() {
        // Shizuku asks the helper process to exit when it is unbound
        System.exit(0);
    }

    @Override
    public String setHomeActivity(String component, int userId) {
        // Same command as "adb shell cmd package set-home-activity"
        return runCommand("cmd", "package", "set-home-activity",
                "--user", String.valueOf(userId), component);
    }

    /** Runs COMMAND, returning its combined output (or the error message upon failure). */
    private static String runCommand(String... command) {
        String result;
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            process.waitFor();
            result = output.toString().trim();
        } catch (IOException | InterruptedException e) {
            result = "Error: " + e;
        }
        return result;
    }
}
