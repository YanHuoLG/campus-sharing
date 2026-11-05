package cn.edu.campus.sharing.auth;

import cn.edu.campus.sharing.common.ApiResponse;
import cn.edu.campus.sharing.security.SessionUser;
import cn.edu.campus.sharing.user.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrf;

    public AuthController(AuthService service, SecurityContextRepository contexts, CsrfTokenRepository csrf) {
        this.service = service;
        this.contexts = contexts;
        this.csrf = csrf;
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfResponse> csrf(CsrfToken token) {
        return ApiResponse.ok(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(service.register(request)));
    }

    @PostMapping("/login")
    public ApiResponse<UserResponse> login(@Valid @RequestBody LoginRequest body,
                                           HttpServletRequest request, HttpServletResponse response) {
        UserResponse user = service.login(body);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(new SessionUser(user.id()),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response);
        csrf.saveToken(null, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return ApiResponse.ok(user);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        csrf.saveToken(null, request, response);
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("JSESSIONID", "")
                .path("/").httpOnly(true).secure(request.isSecure()).sameSite("Lax").maxAge(0).build().toString());
        return ApiResponse.ok(null);
    }

    public record CsrfResponse(String headerName, String token) {}
}
