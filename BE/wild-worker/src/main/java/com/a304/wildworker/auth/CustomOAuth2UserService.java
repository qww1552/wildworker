package com.a304.wildworker.auth;

import com.a304.wildworker.common.Constants;
import com.a304.wildworker.domain.sessionuser.PrincipalDetails;
import com.a304.wildworker.domain.sessionuser.SessionUser;
import com.a304.wildworker.domain.user.User;
import com.a304.wildworker.domain.user.UserRepository;
import com.a304.wildworker.ethereum.exception.WalletCreationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import javax.transaction.Transactional;
import java.util.Collections;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final HttpSession httpSession;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("login - loadUser");
        OAuth2User oAuth2User = super.loadUser(userRequest); // Oath2 정보를 가져옴

        String registrationId = userRequest.getClientRegistration()
                .getRegistrationId(); // 소셜 정보 가져옴
        String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();

        OAuth2Attribute attributes = OAuth2Attribute.of(registrationId, userNameAttributeName,
                oAuth2User.getAttributes());

        User user = findOrSave(attributes);
        SessionUser sessionUser = new SessionUser(user);

        String accessToken = userRequest.getAccessToken().getTokenValue();
        httpSession.setAttribute(Constants.SESSION_NAME_ACCESS_TOKEN, accessToken);
        log.info("- {}: {}", Constants.SESSION_NAME_ACCESS_TOKEN, accessToken);

        return new PrincipalDetails(
                Collections.singleton(new SimpleGrantedAuthority(user.getRole().toString())),
                attributes.getAttributes(),
                attributes.getNameAttributeKey(),
                sessionUser);
    }

    private User findOrSave(OAuth2Attribute attributes) throws WalletCreationException {
        String email = attributes.getEmail();
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> new User(email));

        return userRepository.save(user);
    }

}