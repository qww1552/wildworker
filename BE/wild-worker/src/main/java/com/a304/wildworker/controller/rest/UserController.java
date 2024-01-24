package com.a304.wildworker.controller.rest;

import com.a304.wildworker.domain.sessionuser.PrincipalDetails;
import com.a304.wildworker.domain.sessionuser.SessionUser;
import com.a304.wildworker.dto.request.ChangeUserInfoRequest;
import com.a304.wildworker.dto.response.CoinLogResponse;
import com.a304.wildworker.dto.response.TitleListResponse;
import com.a304.wildworker.dto.response.UserResponse;
import com.a304.wildworker.exception.NotLoginException;
import com.a304.wildworker.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserResponse> getUser(
            @AuthenticationPrincipal PrincipalDetails principal) {
        log.info("GET /user");

        SessionUser user = getSessionUser(principal);

        log.info("- user: {}", user);

        UserResponse response = userService.getUser(user.getEmail());
        return ResponseEntity.ok(response);
    }

    /* 회원정보 수정 */
    @PatchMapping
    public ResponseEntity<Void> changeUserInfo(
            @AuthenticationPrincipal PrincipalDetails principal,
            @RequestBody ChangeUserInfoRequest changeUserInfoRequest) {
        SessionUser user = getSessionUser(principal);

        userService.changeUserInfo(user.getId(), changeUserInfoRequest);

        return ResponseEntity.ok().build();
    }

    /* 보유 칭호목록 조회 */
    @GetMapping("/titles")
    public ResponseEntity<TitleListResponse> getTitles(
            @AuthenticationPrincipal PrincipalDetails principal) {
        SessionUser user = getSessionUser(principal);

        TitleListResponse response = userService.getTitleList(user.getId());
        return ResponseEntity.ok(response);
    }

    /* 코인 내역 조회 */
    @GetMapping("/coin-log")
    public ResponseEntity<CoinLogResponse> getCoinLog(
            @AuthenticationPrincipal PrincipalDetails principal, Pageable pageable) {
        SessionUser user = getSessionUser(principal);

        CoinLogResponse response = userService.getCoinLog(user.getId(), pageable);
        return ResponseEntity.ok(response);
    }

    private static SessionUser getSessionUser(PrincipalDetails principal) {
        SessionUser user = principal.getSessionUser();

        if (user == null) {
            throw new NotLoginException();
        }
        return user;
    }
}