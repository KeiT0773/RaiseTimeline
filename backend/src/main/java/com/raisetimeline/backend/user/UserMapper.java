package com.raisetimeline.backend.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * users の読み書き。SQL は resources/mapper/user/UserMapper.xml。
 */
@Mapper
public interface UserMapper {

	/** 登録する。採番された id は user.id に書き戻される。 */
	void insert(User user);

	/** ユーザー名が使われているか（大文字と小文字を区別しない。lower(username) の一意索引と同じ判定）。 */
	boolean existsByUsernameIgnoreCase(@Param("username") String username);

	/** メールアドレスが使われているか（保存時に小文字にそろえているため、そのまま比べる）。 */
	boolean existsByEmail(@Param("email") String email);

	User findByEmail(@Param("email") String email);

	User findById(@Param("id") long id);

}
