/*
 * Tests for the selection logic of the home switcher.
 *
 * Created with the help of Claude Code using model claude-fable-5-2.
 */

package app.lawnchair.homeswitcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HomeSelectorTest {

    private static final String RELEASE = "app.lawnchair";
    private static final String DEBUG = "app.lawnchair.play.debug";
    private static final String OWN = "app.lawnchair.homeswitcher";
    private static final List<String> BOTH = Arrays.asList(RELEASE, DEBUG);

    @Test
    public void testPickNextToggles() {
        assertEquals(DEBUG, HomeSelector.pickNext(BOTH, RELEASE));
        assertEquals(RELEASE, HomeSelector.pickNext(BOTH, DEBUG));
    }

    @Test
    public void testPickNextFromOtherHome() {
        assertEquals(RELEASE, HomeSelector.pickNext(BOTH, "com.sec.android.app.launcher"));
        assertEquals(RELEASE, HomeSelector.pickNext(BOTH, null));
    }

    @Test
    public void testPickNextNothingToSwitchTo() {
        assertNull(HomeSelector.pickNext(Collections.singletonList(RELEASE), RELEASE));
        assertNull(HomeSelector.pickNext(Collections.<String>emptyList(), RELEASE));
        assertNull(HomeSelector.pickNext(null, RELEASE));
    }

    @Test
    public void testIsLawnchairBuild() {
        assertTrue(HomeSelector.isLawnchairBuild(RELEASE, OWN));
        assertTrue(HomeSelector.isLawnchairBuild(DEBUG, OWN));
        assertFalse(HomeSelector.isLawnchairBuild(OWN, OWN));
        assertFalse(HomeSelector.isLawnchairBuild("app.lawnchairx", OWN));
        assertFalse(HomeSelector.isLawnchairBuild(null, OWN));
    }

    // TODO: add tests for the activity itself (e.g., via Robolectric with a fake Shizuku)
}
