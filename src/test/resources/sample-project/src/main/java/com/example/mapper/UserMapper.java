package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * Find users by optional name and status. Supports paging.
     */
    List<User> findUsers(@Param("name") String name, @Param("status") Integer status);

    /** Load a user together with its department. */
    User selectWithDept(@Param("id") Long id);

    int batchInsert(@Param("list") List<User> users);

    @Select("SELECT COUNT(*) FROM t_user " +
            "WHERE status = #{status}")
    long countByStatus(@Param("status") int status);

    /** Declared but never implemented anywhere. */
    int unboundMethod(Long id);

    default boolean exists(Long id) {
        return selectById(id) != null;
    }

    /** Overloaded default helper that delegates to findUsers. */
    default List<User> findUsers(String name) {
        return findUsers(name, null);
    }
}
