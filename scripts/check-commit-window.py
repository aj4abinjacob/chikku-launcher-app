#!/usr/bin/env python3
"""Guard new release-repository commits using the actual Asia/Kolkata clock."""
from datetime import datetime
import os
import re
import subprocess
from zoneinfo import ZoneInfo

now = datetime.now(ZoneInfo('Asia/Kolkata'))
print('Release commit clock:', now.isoformat())
# Git sets GIT_AUTHOR_DATE itself before invoking pre-commit, even without an override.
# Validate its real instant rather than rejecting every ordinary Git invocation.
for variable in ('GIT_AUTHOR_DATE', 'GIT_COMMITTER_DATE'):
    value = os.environ.get(variable)
    if value:
        identity = subprocess.check_output(['git', 'var', 'GIT_AUTHOR_IDENT' if variable == 'GIT_AUTHOR_DATE' else 'GIT_COMMITTER_IDENT'], text=True)
        match = re.search(r' (\d+) [+-]\d{4}\s*$', identity)
        if not match or abs(now.timestamp() - int(match.group(1))) > 120:
            raise SystemExit('Noncurrent commit-date overrides are not permitted for release preparation.')
if now.weekday() < 5 and 9 <= now.hour < 19:
    raise SystemExit('Commit blocked: weekdays 09:00 through 18:59 Asia/Kolkata. Prepare and validate without committing.')
