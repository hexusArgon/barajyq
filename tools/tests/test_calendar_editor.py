from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "android/app/src/main/java/kz/astana/afisha/ui"

class CalendarEditorTests(unittest.TestCase):
    def test_native_editor_never_writes_or_fakes_success(self):
        text = (JAVA / "calendar/CalendarEditor.kt").read_text()
        for token in ["Intent.ACTION_INSERT", "CalendarContract.Events.CONTENT_URI", "ActivityNotFoundException", "SecurityException", "calendar_unavailable", "draft.beginMillis?.let"]:
            self.assertIn(token, text)
        for token in ["contentResolver.insert", "EXTRA_EVENT_END_TIME", "EXTRA_EVENT_ALL_DAY", "WRITE_CALENDAR", "READ_CALENDAR"]:
            self.assertNotIn(token, text)
        manifest = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text()
        self.assertNotIn("permission.WRITE_CALENDAR", manifest)
        self.assertNotIn("permission.READ_CALENDAR", manifest)
    def test_actions_and_centered_header_are_wired(self):
        feed = (JAVA / "feed/FeedScreen.kt").read_text()
        detail = (JAVA / "detail/EventDetailScreen.kt").read_text()
        self.assertIn("Modifier.align(Alignment.Center).fillMaxWidth(0.44f)", feed)
        self.assertIn("TextButton(onClick = onPlan)", feed)
        for source in [feed, detail]:
            self.assertIn("openCalendarEditor(context,", source)
            self.assertIn("Icons.Filled.VisibilityOff", source)
            self.assertIn("R.string.plan_event", source)
            self.assertNotIn("Text(stringResource(R.string.not_interested)", source)
    def test_localized_labels(self):
        for locale in ["values", "values-ru", "values-kk"]:
            values = {e.attrib["name"]: e.text for e in ET.parse(ROOT / f"android/app/src/main/res/{locale}/strings.xml").getroot().findall("string")}
            for key in ["plan_event", "calendar_unavailable", "calendar_review"]:
                self.assertTrue(values[key])
