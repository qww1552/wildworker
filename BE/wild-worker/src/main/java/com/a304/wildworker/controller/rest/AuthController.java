package com.a304.wildworker.controller.rest;

import com.a304.wildworker.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    @GetMapping("/login")
    public ResponseEntity<Void> login(HttpServletRequest request) {
        URI redirectUri = ServletUriComponentsBuilder.fromContextPath(request)
                .path("/oauth2/authorization/kakao")
                .build().toUri();

        String referer = request.getHeader("Referer");
        if (!StringUtils.hasText(referer)) {
            // 게임 로그인 페이지를 통해서 로그인 해야함
            return ResponseEntity.badRequest().build();
        }
        referer = ServletUriComponentsBuilder.fromHttpUrl(referer).toUriString();

        HttpSession session = request.getSession();
        session.setAttribute(Constants.SESSION_NAME_PREV_PAGE, referer);

        log.info("/auth/login: {}", redirectUri);
        return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
    }
}
