package cn.edu.campus.sharing.security;

import java.io.Serializable;
import java.security.Principal;

// Only an account id is kept in the session, never a password or password hash.
public record SessionUser(long id) implements Principal, Serializable {
    @Override
    public String getName() { return Long.toString(id); }
}
