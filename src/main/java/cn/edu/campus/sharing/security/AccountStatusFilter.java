package cn.edu.campus.sharing.security;

import cn.edu.campus.sharing.common.ApiResponse;
import cn.edu.campus.sharing.user.UserAccount;
import cn.edu.campus.sharing.user.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

public class AccountStatusFilter extends OncePerRequestFilter {
    private final UserMapper users;
    private final ObjectMapper json;

    public AccountStatusFilter(UserMapper users, ObjectMapper json) {
        this.users = users;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SessionUser principal) {
            UserAccount account;
            try {
                account = users.selectById(principal.id());
            } catch (DataAccessException ex) {
                write(response, 503, "账号状态暂时无法验证，请稍后重试");
                return;
            }
            if (account == null || !"ACTIVE".equals(account.getStatus())) {
                if (request.getSession(false) != null) request.getSession(false).invalidate();
                SecurityContextHolder.clearContext();
                write(response, account == null ? 401 : 403,
                        account == null ? "登录已失效，请重新登录" : "账号已被封禁");
                return;
            }
            var fresh = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole())));
            // Use a new context so simultaneous requests do not mutate a shared session context.
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(fresh);
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }

    private void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), ApiResponse.error(status, message));
    }
}
