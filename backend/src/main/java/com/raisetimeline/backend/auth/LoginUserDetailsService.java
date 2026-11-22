package com.raisetimeline.backend.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.raisetimeline.backend.user.User;
import com.raisetimeline.backend.user.UserMapper;

/**
 * ログイン時に、メールアドレスから利用者を探す。
 * 見つからないときの UsernameNotFoundException は、Spring Security が BadCredentialsException に変えるため、
 * 「メールアドレスが無い」と「パスワードが違う」は画面からは区別できない（auth.md F-02）。
 */
@Service
public class LoginUserDetailsService implements UserDetailsService {

	private final UserMapper userMapper;

	public LoginUserDetailsService(UserMapper userMapper) {
		this.userMapper = userMapper;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		User user = userMapper.findByEmail(email);
		if (user == null) {
			throw new UsernameNotFoundException("user not found");
		}
		return new LoginUser(user.getId(), user.getEmail(), user.getPasswordDigest());
	}

}
