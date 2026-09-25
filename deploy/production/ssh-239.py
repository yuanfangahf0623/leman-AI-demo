#!/usr/bin/env python3
"""Run a command on 239 with the local Windows DPAPI-protected credential."""

import subprocess
import sys
from pathlib import Path

import paramiko


ROOT = Path(__file__).resolve().parents[2]
CREDENTIAL = ROOT / ".local-secrets" / "239-ssh.xml"
HOST = "192.168.19.239"


def main() -> int:
    if not CREDENTIAL.is_file():
        print(f"Missing local credential: {CREDENTIAL}", file=sys.stderr)
        return 2
    command = ' '.join(sys.argv[1:]) if len(sys.argv) > 1 else 'hostname'
    ps = (
        f"$c=Import-Clixml -LiteralPath '{CREDENTIAL}'; "
        "[Console]::WriteLine($c.UserName); "
        "[Console]::WriteLine($c.GetNetworkCredential().Password)"
    )
    result = subprocess.run(
        ["powershell", "-NoProfile", "-Command", ps],
        capture_output=True, text=True, check=True,
    )
    username, password = result.stdout.splitlines()[:2]
    client = paramiko.SSHClient()
    client.load_system_host_keys()
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    try:
        client.connect(HOST, username=username, password=password,
                       timeout=10, look_for_keys=False, allow_agent=False)
        _, stdout, stderr = client.exec_command(command, timeout=60)
        out = stdout.read().decode(errors="replace")
        err = stderr.read().decode(errors="replace")
        sys.stdout.write(out)
        sys.stderr.write(err)
        return stdout.channel.recv_exit_status()
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
