package com.a304.wildworker.auth;

import com.a304.wildworker.common.Constants;
import com.a304.wildworker.exception.NotLoginException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Slf4j
@RequiredArgsConstructor
@Service
public class CustomLogoutHandler implements LogoutHandler {

    private final KakaoAPI kakaoAPI;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response,
                       Authentication authentication) {
        HttpSession session = request.getSession(false);
        log.info("POST:/auth/logout: {}", session);
        if (session == null) {
            throw new NotLoginException();
        }

        String accessToken = (String) session.getAttribute(Constants.SESSION_NAME_ACCESS_TOKEN);
        if (StringUtils.hasText(accessToken)) {
            kakaoAPI.logout(accessToken);
            log.info("- logout success: {}", accessToken);
        } else {
            log.info("- logout fails: accessToken is empty");
        }
    }
}
