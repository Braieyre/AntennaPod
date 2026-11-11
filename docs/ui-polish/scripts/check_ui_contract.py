import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[3]
resources = root / 'ui/common/src/main/res/values'
colors = {node.attrib['name']: node.text for node in ET.parse(resources / 'colors.xml').getroot()}
styles = {node.attrib['name']: {item.attrib['name']: item.text for item in node} for node in ET.parse(resources / 'styles.xml').getroot() if node.tag == 'style'}

def resolve(value):
    return colors[value.split('/', 1)[1]] if value.startswith('@color/') else value

def luminance(value):
    channels = [int(value[index:index + 2], 16) / 255 for index in (1, 3, 5)]
    linear = [channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4 for channel in channels]
    return sum(a * b for a, b in zip(linear, (0.2126, 0.7152, 0.0722)))

def contrast(a, b):
    high, low = sorted((luminance(resolve(a)), luminance(resolve(b))), reverse=True)
    return (high + 0.05) / (low + 0.05)

pairs = []
for mode in ('Light', 'Dark'):
    values = styles['Theme.AntennaPod.' + mode]
    for text in ('android:textColorPrimary', 'android:textColorSecondary'):
        for background in ('android:colorBackground', 'colorSurfaceContainer', 'colorSecondaryContainer'):
            ratio = contrast(values[text], values[background])
            assert ratio >= 4.5, (mode, text, background, ratio)
            pairs.append({'theme': mode, 'text': text, 'background': background, 'ratio': round(ratio, 2)})
    accent_ratio = contrast(values['paperSecondaryAccent'], values['colorSecondaryContainer'])
    assert accent_ratio >= 4.5, (mode, 'category accent', accent_ratio)
    pairs.append({'theme': mode, 'text': 'paperSecondaryAccent', 'background': 'colorSecondaryContainer', 'ratio': round(accent_ratio, 2)})
    ratio = contrast(values['colorOnPrimary'], values['colorPrimary'])
    assert ratio >= 4.5, (mode, 'button', ratio)
    pairs.append({'theme': mode, 'text': 'colorOnPrimary', 'background': 'colorPrimary', 'ratio': round(ratio, 2)})

strings = {node.attrib['name']: ''.join(node.itertext()) for node in ET.parse(root / 'ui/i18n/src/main/res/values/strings.xml').getroot() if node.tag == 'string'}
expected = {'paper_programmes': '节目', 'paper_queue': '待播', 'paper_settings': '设置', 'paper_import_history': '导入记录', 'paper_source_deleted': '原文件已删除', 'paper_source_missing': '原文件已不存在'}
for name, text in expected.items():
    assert strings[name] == text, (name, strings[name])
for name, text in strings.items():
    if name.startswith('paper_'):
        assert not re.search(r'WorkManager|FeedItem|入库|条目 ID', text), (name, text)
source = (root / 'app/src/main/java/de/danoeh/antennapod/zipimport/SeriesLibraryFragment.java').read_text()
assert 'MaterialAlertDialogBuilder' not in source
assert 'consumeCompletion' in source
assert 'PaperUi.completed(root)' not in source
history = (root / 'app/src/main/java/de/danoeh/antennapod/zipimport/ImportHistoryFragment.java').read_text()
assert history.count('new MaterialAlertDialogBuilder') == 1
print(json.dumps({'text_contrast_pairs': pairs, 'minimum': min(pair['ratio'] for pair in pairs), 'copy_and_popup_guards': 'PASS', 'scope': 'A native light/dark shared text/button tokens; paper chrome and custom import popups only; imported content excluded'}, ensure_ascii=False, indent=2))
