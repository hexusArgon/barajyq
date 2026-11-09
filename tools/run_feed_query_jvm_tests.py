#!/usr/bin/env python3
"""Compile real query/model/policy + JUnit tests without Android SDK.

Usage: python3 tools/run_feed_query_jvm_tests.py --java /path/to/java --jars /path/to/jars
Jars: Kotlin compiler-embeddable 2.0.21 + its POM runtime dependencies,
JetBrains annotations, JUnit 4.13.2 and hamcrest-core 1.3. No downloads performed.
Only generated R constants and the StringRes annotation are stubbed. This is NOT
an Android/Compose compilation or UI test. Gradle alternative: testDebugUnitTest.
"""
import argparse
from pathlib import Path
import re
import subprocess
import tempfile
ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java',required=True);p.add_argument('--jars',required=True);a=p.parse_args()
    jars=Path(a.jars).resolve()
    # os.pathsep keeps the harness portable when Python is run on Windows.
    import os
    classpath=os.pathsep.join(str(p) for p in sorted(jars.glob('*.jar')))
    sources=[ROOT/'android/app/src/main/java/kz/astana/afisha/domain/Models.kt',
        ROOT/'android/app/src/main/java/kz/astana/afisha/data/EventContentPolicy.kt',
        ROOT/'android/app/src/main/java/kz/astana/afisha/ui/feed/FeedQuery.kt',
        ROOT/'android/app/src/test/java/kz/astana/afisha/ui/feed/FeedQueryTest.kt']
    with tempfile.TemporaryDirectory(prefix='barajyq-query-tests-') as temp:
        work=Path(temp)
        names=sorted(set(re.findall(r'R\.string\.([a-zA-Z0-9_]+)',sources[0].read_text())))
        (work/'R.kt').write_text('package kz.astana.afisha\nobject R { object string {\n'+''.join(f'const val {n}: Int = {i}\n' for i,n in enumerate(names))+'} }\n')
        (work/'StringRes.kt').write_text('package androidx.annotation\nannotation class StringRes\n')
        subprocess.run([a.java,'-cp',classpath,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',classpath,'-d',str(work/'classes'),*[str(s) for s in sources],str(work/'R.kt'),str(work/'StringRes.kt')],check=True)
        subprocess.run([a.java,'-cp',str(work/'classes')+os.pathsep+classpath,'org.junit.runner.JUnitCore','kz.astana.afisha.ui.feed.FeedQueryTest'],check=True)

if __name__=='__main__': main()
