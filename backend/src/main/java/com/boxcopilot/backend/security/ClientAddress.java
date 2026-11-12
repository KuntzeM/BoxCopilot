package com.boxcopilot.backend.security;

import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;

/**
 * The client address login throttling counts by: {@code getRemoteAddr()}, which behind the reverse proxy is the
 * real client only because forwarded headers are honoured for internal proxies alone. IPv6 addresses are reduced
 * to their /64 prefix (one subscriber owns a whole /64, so per-address keys would be free to rotate); IPv4-mapped
 * IPv6 addresses become plain IPv4. Only IP literals are parsed, never resolved.
 */
public final class ClientAddress {

    public static final String UNKNOWN = "unknown";

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request) {
        String address = request == null ? null : request.getRemoteAddr();
        return address == null || address.isBlank() ? UNKNOWN : normalize(address.trim());
    }

    static String normalize(String address) {
        if (!address.contains(":")) {
            return address;
        }
        try {
            InetAddress parsed = InetAddress.getByName(address); // a literal: no DNS lookup
            if (parsed instanceof Inet6Address v6) {
                return HexFormat.of().formatHex(v6.getAddress(), 0, 8) + "::/64";
            }
            return parsed.getHostAddress();
        } catch (UnknownHostException e) {
            return address;
        }
    }
}
