package com.coolapp.medibook.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class ClientIp {

    private ClientIp() {}

    public static String of(HttpServletRequest request) {
        // server.forward-headers-strategy=framework already resolves X-Forwarded-For into remoteAddr
        return request.getRemoteAddr();
    }

    public static String current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return of(attrs.getRequest());
        }
        return null;
    }
}
