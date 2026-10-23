"""Round 16 profile: the Sock Shop row's profile (sockshop-hdf-probe.properties) plus a logged-in session
through MstAuthHandler's per_jvm_cookie mode (register once, attach the session cookies to every request).
Nothing else changes."""
from pathlib import Path

src = Path(r"C:\Users\miaot\Github\MIST\evaluation\suts\sockshop\sockshop-hdf-probe.properties")
dst = Path(r"C:\Users\miaot\Github\MIST\evaluation\suts\sockshop\sockshop-hdf-login.properties")
assert not dst.exists()
t = src.read_text(encoding="utf-8")
t = t.rstrip("\n") + "\n" + """
# ---------------------------------------------------------------------------------------------------
# Round 16 (2026-10-16): the same profile with a logged-in session. MstAuthHandler's per_jvm_cookie mode
# registers one user through the front-end (POST /register) before the first request and attaches the
# session cookies it sets (logged_in, md.sid) to every request.
experiment.name=sockshop_logged_in
auth.mode=per_jvm_cookie
auth.login.url=/register
auth.login.username=mistsaner${unique}
auth.login.password=mistsanerpw
auth.login.body.template={"username":"${username}","password":"${password}","email":"${username}@mist.test","firstName":"m","lastName":"t"}
auth.login.expected.status=200
auth.skip.path.patterns=/register,/login
"""
dst.write_text(t, encoding="utf-8", newline="")
print("wrote", dst.name, len(t.splitlines()), "lines")
