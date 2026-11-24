/*
 * Selection logic for the home switcher, which is kept free of Android classes for testing.
 *
 * Created with the help of Claude Code using model claude-fable-5-2.
 */

package app.lawnchair.homeswitcher;

import java.util.List;

/** Decides which of the installed Lawnchair builds to switch to. */
public final class HomeSelector {

    /** Prefix of the package names for Lawnchair builds (e.g., app.lawnchair.play.debug). */
    public static final String LAWNCHAIR_PREFIX = "app.lawnchair";

    private HomeSelector() {
    }

    /** Whether PACKAGE_NAME is a Lawnchair build, other than the switcher (OWN_PACKAGE). */
    public static boolean isLawnchairBuild(String packageName, String ownPackage) {
        return packageName != null
                && !packageName.equals(ownPackage)
                && (packageName.equals(LAWNCHAIR_PREFIX)
                        || packageName.startsWith(LAWNCHAIR_PREFIX + "."));
    }

    /**
     * Returns the entry of PACKAGES after CURRENT, wrapping around; the first one is used
     * if CURRENT is not among them (e.g., another home app is the default).
     * Returns null unless there is something to switch to.
     */
    public static String pickNext(List<String> packages, String current) {
        String next = null;
        if (packages != null && !packages.isEmpty()) {
            int index = packages.indexOf(current);
            boolean onlyCurrent = (index >= 0) && (packages.size() == 1);
            if (!onlyCurrent) {
                next = packages.get((index + 1) % packages.size());
            }
        }
        return next;
    }
}
