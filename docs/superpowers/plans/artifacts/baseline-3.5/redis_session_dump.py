#!/usr/bin/env python3
"""Dump every spring:session:sessions:* hash from a Redis (plain RESP over a socket, no client library).
Usage: REDIS_PORT=56379 REDIS_PASSWORD=... python redis_session_dump.py > redis-session-fixture.txt"""
import base64, os, socket

s = socket.create_connection(("localhost", int(os.environ.get("REDIS_PORT", "6379"))))
f = s.makefile("rb")


def cmd(*a):
    out = b"*%d\r\n" % len(a)
    for x in a:
        x = x if isinstance(x, bytes) else str(x).encode()
        out += b"$%d\r\n%s\r\n" % (len(x), x)
    s.sendall(out)
    return read()


def read():
    line = f.readline().rstrip(b"\r\n")
    t, rest = line[:1], line[1:]
    if t in b"+:":
        return rest
    if t == b"-":
        raise RuntimeError(rest.decode())
    if t == b"$":
        n = int(rest)
        if n < 0:
            return None
        d = f.read(n + 2)[:-2]
        return d
    if t == b"*":
        return [read() for _ in range(int(rest))]
    raise RuntimeError(line)


cmd("AUTH", os.environ["REDIS_PASSWORD"])
keys = sorted(cmd("KEYS", "spring:session:*"))
print("# Redis %s; keys matching spring:session:*: %d" % (
    next(l for l in cmd("INFO", "server").decode().splitlines() if l.startswith("redis_version")), len(keys)))
for k in keys:
    print("\nKEY %s" % k.decode())
    print("TYPE %s  TTL %s s" % (cmd("TYPE", k).decode(), cmd("TTL", k).decode()))
    h = cmd("HGETALL", k)
    for name, val in zip(h[::2], h[1::2]):
        print("FIELD %s" % name.decode())
        print("  length %d bytes; starts with %s" % (len(val), val[:4].hex()))
        print("  escaped: %s" % val.decode("latin-1").encode("unicode_escape").decode())
        print("  base64:  %s" % base64.b64encode(val).decode())
    print("DUMP(base64, RESTORE-able): %s" % base64.b64encode(cmd("DUMP", k)).decode())
