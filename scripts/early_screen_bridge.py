#!/usr/bin/env python3
"""Bounded early-display test, using shell hinge events without screen capture.
Needs build/hinge-signal/classes.dex. Never starts an effect or changes lock settings.
"""
import argparse
import json
import os
from pathlib import Path
import re
import selectors
import signal
import subprocess
import time


def next_action(previous, value, owned, early_cover=False):
    if owned and value in (0, 180):
        return "reset"
    if not owned and previous == 0 and value == 90:
        return "open"
    if early_cover and not owned and previous == 180 and value == 90:
        return "cover"
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default=os.environ.get("ADB", "adb"))
    parser.add_argument("--seconds", type=int, default=120)
    parser.add_argument("--early-cover", action="store_true",
                        help="Experimental: request TENT(1) on 180→90 for earlier cover content")
    args = parser.parse_args()
    if not 1 <= args.seconds <= 300:
        parser.error("--seconds must be between 1 and 300")
    adb = [args.adb, "-s", args.serial]
    def shell(*command):
        return subprocess.run(adb + ["shell", *command], check=True, text=True,
                              capture_output=True, timeout=8).stdout
    initial = shell("dumpsys", "device_state")
    if "mOverrideState=Optional.empty" not in initial:
        raise RuntimeError("Existing override: leaving it untouched")
    if "identifier=3, name='OPENED'" not in initial:
        raise RuntimeError("This device does not have the validated OPENED state 3")
    if args.early_cover and "identifier=1, name='TENT'" not in initial:
        raise RuntimeError("This device does not expose TENT state 1")
    dex = Path(__file__).resolve().parent.parent / "build/hinge-signal/classes.dex"
    subprocess.run(adb + ["push", str(dex), "/data/local/tmp/duo-hinge-signal.dex"], check=True, capture_output=True)
    reader = subprocess.Popen(adb + ["shell", "CLASSPATH=/data/local/tmp/duo-hinge-signal.dex",
                                    "app_process", "/system/bin", "HingeSignal", str(args.seconds)],
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    selector = selectors.DefaultSelector()
    selector.register(reader.stdout, selectors.EVENT_READ)
    owned = False
    previous = None
    buffer = b""
    deadline = time.monotonic() + args.seconds
    def emit(**event):
        print(json.dumps(dict(monotonic=time.monotonic(), **event)), flush=True)
    def await_base(value):
        # The public hinge endpoint can precede the policy's base-state update.
        # Releasing TENT too soon briefly selects the inner panel again.
        expected = 0 if value == 0 else 3
        stop = time.monotonic() + 1.0
        consecutive = 0
        while time.monotonic() < stop:
            state = shell("dumpsys", "device_state")
            match = re.search(r"mBaseState=Optional\[DeviceState\{identifier=(\d+),", state)
            consecutive = consecutive + 1 if match and int(match.group(1)) == expected else 0
            if consecutive >= 2:
                emit(event="base_ready", state=expected)
                return
            time.sleep(.05)
        emit(event="base_wait_timeout", expected=expected)

    def terminate(signum, frame):
        raise KeyboardInterrupt
    signal.signal(signal.SIGTERM, terminate)
    emit(event="ready", seconds=args.seconds, requires_projection=False, early_cover=args.early_cover)
    try:
        while time.monotonic() < deadline and reader.poll() is None:
            for key, _ in selector.select(timeout=.5):
                buffer += os.read(key.fd, 4096)
                while b"\n" in buffer:
                    raw, buffer = buffer.split(b"\n", 1)
                    match = re.fullmatch(rb"HINGE=([0-9.]+)\r?", raw)
                    if not match:
                        continue
                    value = float(match.group(1))
                    action = next_action(previous, value, owned, args.early_cover)
                    emit(event="hinge", value=value)
                    if action in ("open", "cover"):
                        # Mark ownership first so an uncertain command is reset in finally.
                        owned = True
                        shell("cmd", "device_state", "state", "3" if action == "open" else "1")
                        emit(event="inner_requested" if action == "open" else "cover_requested")
                    elif action == "reset":
                        await_base(value)
                        shell("cmd", "device_state", "state", "reset")
                        owned = False
                        emit(event="automatic_restored")
                    previous = value
    except KeyboardInterrupt:
        pass
    finally:
        if owned:
            try:
                shell("cmd", "device_state", "state", "reset")
                emit(event="automatic_restored_on_exit")
            except Exception as error:
                emit(event="restore_failed", error=str(error), action="Reconnect ADB and run cmd device_state state reset")
        reader.terminate()
        try:
            reader.wait(timeout=3)
        except subprocess.TimeoutExpired:
            reader.kill()
            reader.wait()
        selector.close()
        emit(event="finished")


if __name__ == "__main__":
    main()
