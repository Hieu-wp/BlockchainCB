package com.blockchaincb.demo.blockchain;

import com.blockchaincb.demo.exception.ApiException;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Server-owned exact origins. Never derive signing scope from an untrusted Host header. */
@Component
public class WalletOriginPolicy {
  private final String primary;
  private final List<String> allowed;

  public WalletOriginPolicy(ChainSettings settings,
      @Value("${doctor.wallet.allowed-origins:}") String configured) {
    if (configured.chars().anyMatch(c -> c < 32))
      throw new IllegalArgumentException("Invalid Doctor wallet origin configuration");
    String domain = settings.domain.trim();
    if (domain.contains("://")) primary = canonical(domain);
    else {
      URI candidate = URI.create("http://" + domain);
      primary = canonical((loopback(candidate.getHost()) ? "http://" : "https://") + domain);
    }
    var origins = new LinkedHashSet<String>();
    if (configured.isBlank()) {
      origins.add(primary);
      URI uri = URI.create(primary);
      // Both common local entry URLs, same scheme and explicit port, remain separately bound in the message.
      if (loopback(uri.getHost())) {
        String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
        origins.add(uri.getScheme() + "://localhost" + port);
        origins.add(uri.getScheme() + "://127.0.0.1" + port);
        origins.add(uri.getScheme() + "://[::1]" + port);
      }
    } else {
      for (String origin : configured.split(",", -1)) origins.add(canonical(origin.trim()));
    }
    allowed = List.copyOf(origins);
  }

  private static boolean loopback(String host) {
    return host != null && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(host.toLowerCase(Locale.ROOT));
  }

  private static String canonical(String raw) {
    try {
      if (raw == null || raw.isBlank() || raw.contains("\r") || raw.contains("\n")) throw new IllegalArgumentException();
      URI uri = URI.create(raw);
      String scheme = uri.getScheme(), host = uri.getHost();
      if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
          || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
          || !("https".equals(scheme) || ("http".equals(scheme) && loopback(host)))
          || uri.getPort() > 65535 || uri.getPort() == 0) throw new IllegalArgumentException();
      String canonical = scheme + "://" + uri.getRawAuthority().toLowerCase(Locale.ROOT);
      if (!raw.equals(canonical)) throw new IllegalArgumentException();
      return canonical;
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("Invalid Doctor wallet origin configuration");
    }
  }

  public String primaryOrigin() { return primary; }
  public String primaryDomain() { return URI.create(primary).getRawAuthority(); }
  public List<String> allowedOrigins() { return allowed; }

  public String requireAllowed(String requested) {
    try {
      String origin = canonical(requested);
      if (allowed.contains(origin)) return origin;
    } catch (IllegalArgumentException invalid) {
      // Return one safe error rather than echoing an arbitrary URL or parser exception.
    }
    throw new ApiException(403, "WALLET_ORIGIN_NOT_ALLOWED");
  }
}
