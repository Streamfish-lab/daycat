# -*- coding: utf-8 -*-
"""打包前检查：JS 里绑定的 #id 是否都在 HTML 里存在。
曾经因为 id="sTestNoti" 与 $('#sTestNotify') 差一个字母，
「测试通知」按钮静默失效了很久。"""
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PATH = os.path.join(HERE, '..', 'app-src', 'assets', 'www', 'index.html')
s = io.open(PATH, encoding='utf-8').read()

ids = set(re.findall(r'id="([A-Za-z0-9_-]+)"', s))
ids |= set(re.findall(r"id='([A-Za-z0-9_-]+)'", s))
refs = set(re.findall(r"\$\('#([A-Za-z0-9_-]+)'", s))

IGNORE = {'syncBox'}
bad = sorted(r for r in refs if r not in ids and r not in IGNORE and not r.startswith('v-'))
print('missing ids:', bad if bad else 'none')
sys.exit(0)
