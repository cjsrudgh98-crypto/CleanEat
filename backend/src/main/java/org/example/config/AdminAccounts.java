package org.example.config;

import lombok.RequiredArgsConstructor;
import org.example.domain.Role;
import org.example.domain.User;
import org.example.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 관리자 계정 지정. 설정(app.admin.usernames, 환경변수 ADMIN_USERNAMES)에 적힌 아이디는
 * 서버 시작 시 / 가입 시 ADMIN 권한을 받는다. 설정에서 빼면 다음 시작 때 일반 회원으로 돌아간다.
 * (관리자를 웹 화면에서 지정하게 하면 권한 탈취 위험이 커서, 서버 설정으로만 정한다)
 */
@Component
@RequiredArgsConstructor
public class AdminAccounts implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccounts.class);

    private final UserRepository userRepository;

    @Value("${app.admin.usernames:}")
    private String adminUsernames;

    public boolean isAdmin(String username) {
        return usernames().contains(username);
    }

    private Set<String> usernames() {
        return Arrays.stream(adminUsernames.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> admins = usernames();
        for (User user : userRepository.findAll()) {
            Role expected = admins.contains(user.getUsername()) ? Role.ADMIN : Role.USER;
            if (user.getRole() != expected) {
                user.setRole(expected);
                log.info("권한 변경: {} -> {}", user.getUsername(), expected);
            }
        }
    }
}
