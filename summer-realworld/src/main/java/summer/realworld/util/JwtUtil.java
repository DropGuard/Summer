package summer.realworld.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import summer.core.config.ConfigurationBinder;

public class JwtUtil {

	public record JwtProperties(String secret) {
	}

	private static final JwtProperties PROPS = loadProperties();
	private static final long ACCESS_TOKEN_EXPIRATION = 15 * 60 * 1000; // 15 minutes
	private static final long REFRESH_TOKEN_EXPIRATION = 7 * 24 * 60 * 60 * 1000; // 7 days

	private static final SecretKey key = Keys.hmacShaKeyFor(PROPS.secret().getBytes());

	private static JwtProperties loadProperties() {
		return ConfigurationBinder.bind("application.yml", JwtProperties.class, "jwt");
	}

	public static String generateAccessToken(Long userId, String username, String email) {
		return Jwts.builder().subject(String.valueOf(userId)).claim("username", username).claim("email", email)
				.claim("type", "access").issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION)).signWith(key).compact();
	}

	public static String generateRefreshToken(Long userId) {
		return Jwts.builder().subject(String.valueOf(userId)).claim("type", "refresh").issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION)).signWith(key).compact();
	}

	public static Claims parseToken(String token) {
		return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
	}

	public static Long getUserIdFromToken(String token) {
		Claims claims = parseToken(token);
		return Long.parseLong(claims.getSubject());
	}

	public static String getUsernameFromToken(String token) {
		Claims claims = parseToken(token);
		return claims.get("username", String.class);
	}

	public static String getEmailFromToken(String token) {
		Claims claims = parseToken(token);
		return claims.get("email", String.class);
	}

	public static String getTokenType(String token) {
		Claims claims = parseToken(token);
		return claims.get("type", String.class);
	}

	public static boolean isTokenExpired(String token) {
		try {
			Claims claims = parseToken(token);
			return claims.getExpiration().before(new Date());
		} catch (Exception e) {
			return true;
		}
	}

	public static boolean isAccessToken(String token) {
		return "access".equals(getTokenType(token));
	}

	public static boolean isRefreshToken(String token) {
		return "refresh".equals(getTokenType(token));
	}
}