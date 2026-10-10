package com.example.service;

import com.example.entity.User;

import java.util.List;

/** Not a mapper: must not appear in the output. */
public interface UserService {

    List<User> findUsers(String name, Integer status);
}
