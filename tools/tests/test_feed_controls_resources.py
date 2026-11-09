"""Resource and integration guards; executable query tests live in JUnit."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
UI=ROOT/'android/app/src/main/java/kz/astana/afisha/ui/feed'

class FeedControlsResourceTests(unittest.TestCase):
    def test_toolbar_is_single_compact_row_with_permanent_input(self):
        source = (UI / 'FeedTools.kt').read_text()
        toolbar = source[source.index('    val searchLabel'):source.index('    if (dialog == "filters")')]
        for text in ['BasicTextField(', 'Modifier.weight(1f).widthIn(min = 0.dp)',
                     'singleLine = true', 'Modifier.size(48.dp)', 'contentDescription = searchLabel',
                     'if (options.query.isNotEmpty()) IconButton']:
            self.assertIn(text, toolbar)
        for text in ['FlowRow(', 'OutlinedButton(', 'OutlinedTextField(', 'tools_search))', 'Text(filterLabel)', 'Text(sortDescription)']:
            self.assertNotIn(text, toolbar)

    def test_sections_are_fixed_bottom_horizontal_items(self):
        screen = (UI / 'FeedScreen.kt').read_text()
        self.assertIn('bottomBar = {', screen)
        self.assertIn('Column(Modifier.navigationBarsPadding())', screen)
        self.assertEqual(screen.count('Modifier.weight(1f)) { tab ='), 2)
        self.assertIn('role = Role.Tab', screen)
        self.assertIn('heightIn(min = 48.dp)', screen)
        self.assertNotIn('PrimaryTabRow(', screen)
        section = screen[screen.index('private fun BottomSection'):screen.index('@OptIn(ExperimentalLayoutApi::class)', screen.index('private fun BottomSection'))]
        self.assertLess(section.index('Icon(icon'), section.index('Text(label'))
        self.assertIn('Row(Modifier.fillMaxWidth()', section)
        self.assertIn('Modifier.fillMaxSize().padding(pad)', screen)

    def test_compact_vertical_stack_keeps_card_spacing_and_system_insets(self):
        screen = (UI / 'FeedScreen.kt').read_text()
        tools = (UI / 'FeedTools.kt').read_text()
        self.assertIn('statusBarsPadding().heightIn(min = 48.dp)', screen)
        self.assertIn('contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp)', screen)
        self.assertIn('Modifier.padding(start = 4.dp, bottom = 4.dp)', screen)
        self.assertIn('padding(top = if (index == 0) 0.dp else 16.dp)', screen)
        self.assertIn('Column(Modifier.navigationBarsPadding())', screen)
        self.assertIn('selectableGroup().padding(horizontal = 12.dp)', screen)
        self.assertNotIn('padding(horizontal = 16.dp, vertical = 4.dp)', tools)

    def test_complete_ru_kk_en_tool_strings(self):
        refs=set(re.findall(r'R\.string\.(tools_\w+)',(UI/'FeedTools.kt').read_text()+(UI/'FeedScreen.kt').read_text()))
        for folder in ('values','values-ru','values-kk'):
            root=ET.parse(ROOT/f'android/app/src/main/res/{folder}/strings.xml').getroot()
            strings={e.attrib['name']:e.text for e in root.findall('string')}
            self.assertFalse(refs-strings.keys(),folder)
            for name in refs:self.assertTrue(strings[name],(folder,name))
            self.assertEqual(set(re.findall(r'%\d+\$[ds]',strings['tools_active_filters'])),{'%1$d'})
            self.assertEqual(set(re.findall(r'%\d+\$[ds]',strings['tools_current_sort'])),{'%1$s'})
    def test_separate_saveable_tabs_and_profile_scope_before_query(self):
        screen=(UI/'FeedScreen.kt').read_text()
        for text in ['var eventOptions by rememberSaveable(stateSaver = QueryOptionsSaver)',
                     'var placeOptions by rememberSaveable(stateSaver = QueryOptionsSaver)',
                     'FeedRepository.forProfile','it.interest !in p.exclusions','FeedQuery.apply(baseItems',
                     'if (tab == 0) eventOptions = next else placeOptions = next','R.string.tools_no_results','R.string.tools_reset_all']:
            self.assertIn(text,screen)
        self.assertNotIn('SessionStore.setInterests',screen)
        self.assertNotIn('SessionStore.clear',screen)
    def test_material_controls_have_dismiss_clear_and_touch_targets(self):
        controls=(UI/'FeedTools.kt').read_text()
        for text in ['ModalBottomSheet(onDismissRequest = onDismiss','AlertDialog(onDismissRequest',
                     'Icons.Filled.Search','Icons.Filled.Tune','Icons.Filled.Sort',
                     'R.string.tools_clear_search','R.string.tools_cancel',
                     'Modifier.heightIn(min = 48.dp)','FlowRow','verticalScroll','draft.resetFilters()']:
            self.assertIn(text,controls)

if __name__=='__main__': unittest.main()
